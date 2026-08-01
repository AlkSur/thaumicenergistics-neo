package thaumicenergistics.common.parts;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.visnet.VisNetHandler;
import thaumcraft.common.blockentities.VisRelayBlockEntity;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.grid.IDigiVisSource;
import thaumicenergistics.common.integration.tc.VisProviderProxy;

import java.util.List;

/**
 * 桥接 AE2 ME 网络与 TC vis 网络。作为 P2P 隧道成对连接两端：
 * 面对 TC4 {@link VisRelayBlockEntity} 的一端可从其中抽取 vis（消耗 AE 能量），
 * 该 vis 对隧道其余端可用。不面对中继但与中继端相连的端，
 * 会向 TC 的 {@link VisNetHandler} 注册虚拟 {@link VisProviderProxy}，
 * 使附近 TC 机器（法杖、奥术工作台等）可直接从 AE 网格吸取 vis。
 * 自 1.7.10 (RV3 PartVisInterface) 移植，适配 1.21.1 AE2 P2P + ResourceLocation-aspect API。
 */
public class VisInterfacePart extends P2PTunnelPart<VisInterfacePart> implements IGridTickable, IDigiVisSource {

    public static final ResourceLocation MODEL_VIS_INTERFACE = ThaumicEnergistics.id("parts/p2p/p2p_tunnel_vis");

    private static final P2PModels MODELS = new P2PModels(MODEL_VIS_INTERFACE);

    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    /** 每次成功 vis 请求消耗的 AE 能量（AE 单位）。固定成本，与 RV3 一致。 */
    private static final double POWER_PER_REQUESTED_VIS = 4.0;

    /** 抽取颜色在没有新抽取后保持点亮的时间（ms），超时清除。 */
    private static final long TIME_TO_CLEAR = 500L;

    private static final String NBT_KEY_UID = "uid";
    private static final String NBT_KEY_IS_PROVIDER = "isProvider";

    private long uid = 0;
    private boolean isProvider = false;

    /** 当前抽取中的要素颜色（用于动画眼睛）；0 = 空闲。 */
    private int visDrainingColor = 0;
    private long lastColorUpdate = 0;

    /** 为 provider 端注册到 VisNetHandler 的虚拟 vis 源。 */
    private VisProviderProxy visProviderProxy = null;
    private boolean providerActive = false;

    /** consumeVis 跨 P2P 端委托的重入保护。 */
    private boolean inConsume = false;

    public VisInterfacePart(IPartItem<?> partItem) {
        super(partItem);
        this.uid = System.currentTimeMillis() ^ this.hashCode();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(4, 4, 14, 12, 12, 15);
        bch.addBox(5, 5, 13, 11, 11, 14);
    }

    @Override
    public int getLightLevel() {
        return isActive() ? 8 : 0;
    }

    public long getUID() {
        return uid;
    }

    public boolean isVisProvider() {
        return isProvider;
    }

    public void setIsVisProvider(boolean provider) {
        this.isProvider = provider;
        if (getHost() != null) {
            getHost().markForUpdate();
        }
    }

    public IGrid getGrid() {
        if (this.getMainNode() == null) return null;
        return this.getMainNode().getGrid();
    }

    // ===== IDigiVisSource =====

    @Override
    public boolean isActive() {
        return super.isActive();
    }

    /**
     * 抽取指定要素的 vis，返回实际获得的量（centivis）。
     * 解析隧道中真正面对中继的一端并从其抽取，消耗 AE 能量支付传输。
     */
    @Override
    public int consumeVis(ResourceLocation aspectId, int amount) {
        if (!isActive() || inConsume) {
            return 0;
        }
        inConsume = true;
        try {
            VisInterfacePart src = findRelayEnd();
            if (src == null) {
                return 0;
            }
            if (src == this) {
                return drawFromRelay(getRelay(), aspectId, amount);
            }
            // 委托给真正面对中继的那一端。
            return src.consumeVis(aspectId, amount);
        } finally {
            inConsume = false;
        }
    }

    // ===== Relay 检测与 vis 抽取 =====

    /**
     * 该部件面对的 TC4 vis 中继，无则 null。中继对 vis 传输是全向的，无需朝向检查。
     */
    private VisRelayBlockEntity getRelay() {
        Level level = getLevel();
        if (level == null || getBlockEntity() == null) {
            return null;
        }
        BlockPos front = getBlockEntity().getBlockPos().relative(getSide());
        BlockEntity be = level.getBlockEntity(front);
        if (be instanceof VisRelayBlockEntity relay) {
            return relay;
        }
        return null;
    }

    /**
     * 找到面对中继的隧道端：优先 P2P 输入端，其次任一输出端，最后自身。
     */
    private VisInterfacePart findRelayEnd() {
        VisInterfacePart input = getInput();
        if (input != null && input.getRelay() != null) {
            return input;
        }
        for (VisInterfacePart output : getOutputs()) {
            if (output.getRelay() != null) {
                return output;
            }
        }
        if (getRelay() != null) {
            return this;
        }
        return null;
    }

    private int drawFromRelay(VisRelayBlockEntity relay, ResourceLocation aspectId, int centivis) {
        if (relay == null) {
            return 0;
        }
        Aspect aspect = rlToAspect(aspectId);
        if (aspect == null) {
            return 0;
        }
        IEnergyService energy = getMainNode() != null && getMainNode().getGrid() != null
                ? getMainNode().getGrid().getEnergyService()
                : null;
        if (energy == null) {
            return 0;
        }
        // 先确认能量足够支付传输成本。
        double simulated = energy.extractAEPower(POWER_PER_REQUESTED_VIS, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated < POWER_PER_REQUESTED_VIS) {
            return 0;
        }
        int amountReceived = relay.consumeVis(aspect, centivis);
        if (amountReceived > 0) {
            energy.extractAEPower(POWER_PER_REQUESTED_VIS, Actionable.MODULATE, PowerMultiplier.CONFIG);
            setDrainColor(aspect.color());
        }
        return amountReceived;
    }

    // ===== Provider（VisNetHandler 源）管理 =====

    /**
     * 该端自身不面对中继、但隧道另一端面对时，作为 TC4 网络的 vis 源。
     */
    private boolean shouldProvide() {
        if (getRelay() != null) {
            return false;
        }
        VisInterfacePart relayEnd = findRelayEnd();
        return relayEnd != null && relayEnd != this;
    }

    private void registerProvider() {
        Level level = getLevel();
        if (level == null || getBlockEntity() == null) {
            return;
        }
        if (visProviderProxy != null) {
            providerActive = true;
            return;
        }
        BlockPos front = getBlockEntity().getBlockPos().relative(getSide());
        BlockState state = level.getBlockState(front);
        VisProviderProxy proxy = new VisProviderProxy(front, state);
        proxy.bind(this, level);
        VisNetHandler.addSource(level, proxy);
        this.visProviderProxy = proxy;
        this.providerActive = true;
        this.isProvider = true;
    }

    private void removeProvider() {
        if (visProviderProxy != null) {
            VisNetHandler.removeNode(visProviderProxy);
            this.visProviderProxy = null;
        }
        this.providerActive = false;
        this.isProvider = false;
    }

    private void setDrainColor(int color) {
        if (color != 0) {
            if (color == visDrainingColor) {
                lastColorUpdate = System.currentTimeMillis();
                return;
            }
            if ((System.currentTimeMillis() - lastColorUpdate) <= (TIME_TO_CLEAR / 2)) {
                return;
            }
            lastColorUpdate = System.currentTimeMillis();
        }
        this.visDrainingColor = color;
        if (getHost() != null) {
            getHost().markForUpdate();
        }
    }

    // ===== Ticking =====

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(30, 30, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            removeProvider();
            return TickRateModulation.SAME;
        }
        if (shouldProvide() && !providerActive) {
            registerProvider();
        } else if (!shouldProvide() && providerActive) {
            removeProvider();
        }
        if (visDrainingColor != 0 && (System.currentTimeMillis() - lastColorUpdate) > TIME_TO_CLEAR) {
            setDrainColor(0);
        }
        return TickRateModulation.SAME;
    }

    // ===== NBT =====

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        this.uid = tag.getLong(NBT_KEY_UID);
        this.isProvider = tag.getBoolean(NBT_KEY_IS_PROVIDER);
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        tag.putLong(NBT_KEY_UID, this.uid);
        tag.putBoolean(NBT_KEY_IS_PROVIDER, this.isProvider);
    }

    // ===== Aspect <-> ResourceLocation 辅助 =====

    private static Aspect rlToAspect(ResourceLocation rl) {
        return Aspect.get(rl.getPath());
    }
}
