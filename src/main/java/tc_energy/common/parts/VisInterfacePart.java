package tc_energy.common.parts;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.node.VisNetworkApi;
import dev.tc4port.thaumcraft.block.entity.VisRelayBlockEntity;
import tc_energy.ThaumicEnergistics;
import tc_energy.api.grid.IDigiVisSource;

import java.util.List;

/**
 * 桥接 AE2 ME 网络与 Ren 版 TC vis 网络。作为 P2P 隧道成对连接两端：
 * 面对 TC4 中继（VisRelayBlockEntity）的一端可以从其中抽取 vis（消耗 AE 能量），
 * 该 vis 对隧道另端可用。不面对中继但与中继端相连的端，
 * 会向 TC 的 vis 网络注册外部源（VisNetworkApi.registerSource），
 * 使附近 TC 机器（法杖充能座等）可直接从 AE 网格吸取 vis。
 * 自 1.7.10 (RV3 PartVisInterface) 移植，适配 Ren 版 VisNetworkApi。
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

    /** 懒 tick 周期：固定 30 tick（vis 传输为低频操作，无需实时轮询）。 */
    private static final int TICK_RATE = 30;

    private static final String NBT_KEY_UID = "uid";
    private static final String NBT_KEY_IS_PROVIDER = "isProvider";

    private long uid = 0;
    private boolean isProvider = false;

    /** 当前抽取中的要素颜色（用于动画猫眼）；0 = 空闲。 */
    private int visDrainingColor = 0;
    private long lastColorUpdate = 0;

    /** provider 端是否已向 vis 网络注册外部源。 */
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
                return drawFromRelay(getRelayPos(), aspectId, amount);
            }
            // 委托给真正面对中继的那一端。
            return src.consumeVis(aspectId, amount);
        } finally {
            inConsume = false;
        }
    }

    // ===== Relay 检测与 vis 抽取 =====

    /**
     * 该部件面对的 TC4 vis 中继位置，无则 null。中继对 vis 传输是双向的，无需朝向检查。
     */
    private BlockPos getRelayPos() {
        Level level = getLevel();
        if (level == null || getBlockEntity() == null) {
            return null;
        }
        BlockPos front = getBlockEntity().getBlockPos().relative(getSide());
        BlockEntity be = level.getBlockEntity(front);
        if (be instanceof VisRelayBlockEntity) {
            return front;
        }
        return null;
    }

    /** 供 vis 网络 provider 使用：找面对中继的隧道端（公开包装）。 */
    public VisInterfacePart findRelayEndForNetwork() {
        if (!isActive()) {
            return null;
        }
        return findRelayEnd();
    }

    /** 本部件面对的中继位置（公开包装，供 vis 网络 provider 声明 networkEndpoint）。 */
    public BlockPos getFrontRelayPos() {
        return getRelayPos();
    }

    /** vis 网络 SIMULATE：能否供给指定量（仅检查 AE 能量预算）。 */
    public int simulateVisDraw(ResourceLocation aspectId, int amount) {
        if (!isActive()) {
            return 0;
        }
        IEnergyService energy = getMainNode() != null && getMainNode().getGrid() != null
                ? getMainNode().getGrid().getEnergyService()
                : null;
        if (energy == null) {
            return 0;
        }
        double simulated = energy.extractAEPower(POWER_PER_REQUESTED_VIS, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return simulated >= POWER_PER_REQUESTED_VIS ? amount : 0;
    }

    /**
     * 找到面对中继的隧道端：优先 P2P 输入端，其次任一输出端，最后自身。
     * 注意：内部使用 super.getOutputs() 而非 getOutputs()，避免覆写导致递归。
     */
    private VisInterfacePart findRelayEnd() {
        VisInterfacePart input = getInput();
        if (input != null && input.getRelayPos() != null) {
            return input;
        }
        for (VisInterfacePart output : super.getOutputs()) {
            if (output.getRelayPos() != null) {
                return output;
            }
        }
        if (getRelayPos() != null) {
            return this;
        }
        return null;
    }

    /**
     * 覆写输出端列表：单 P2P 场景下（装配器直接按频率查输入，无标准 P2P 输出端），
     * 若存在有效的中继端（findRelayEnd 非空，含自身面对中继的情况），
     * 将其视为已链接，使 AE2 P2P 状态显示（Jade/TOP）不再显示 Unlinked"。
     */
    @Override
    public List<VisInterfacePart> getOutputs() {
        List<VisInterfacePart> outputs = super.getOutputs();
        if (!outputs.isEmpty()) {
            return outputs;
        }
        VisInterfacePart relayEnd = findRelayEnd();
        if (relayEnd != null) {
            // 听自身面对中继的情况：返回 relayEnd（可能是 this），让 P2P 状态显示已链接
            return relayEnd == this ? List.of(this) : List.of(relayEnd);
        }
        return outputs;
    }

    private int drawFromRelay(BlockPos relayPos, ResourceLocation aspectId, int centivis) {
        if (relayPos == null) {
            return 0;
        }
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        VisChannel channel = VisChannel.fromAspectId(
                dev.tc4port.thaumcraft.api.aspect.AspectId.of(aspectId)).orElse(null);
        if (channel == null) {
            return 0; // vis 只能按 6 原质通道抽取
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
        // Ren 版：对中继做原生 drain（SIMULATE→EXECUTE 两段）。
        int sim = VisNetworkApi.drainRelay(serverLevel, relayPos, channel, centivis, VisAction.SIMULATE);
        if (sim <= 0) {
            return 0;
        }
        int moved = VisNetworkApi.drainRelay(serverLevel, relayPos, channel, sim, VisAction.EXECUTE);
        if (moved > 0) {
            energy.extractAEPower(POWER_PER_REQUESTED_VIS, Actionable.MODULATE, PowerMultiplier.CONFIG);
            var def = dev.tc4port.thaumcraft.api.aspect.AspectApi.get(channel.aspectId());
            setDrainColor(def != null ? def.color() : 0);
        }
        return moved;
    }

    // ===== Provider（vis 网络外部源）管理 =====

    /**
     * 该端自身不面对中继、但隧道另一端面对时，作为 TC vis 网络的外部 vis 源。
     */
    private boolean shouldProvide() {
        if (getRelayPos() != null) {
            return false;
        }
        VisInterfacePart relayEnd = findRelayEnd();
        return relayEnd != null && relayEnd != this;
    }

    private void registerProvider() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel) || getBlockEntity() == null) {
            return;
        }
        if (providerActive) {
            return;
        }
        // Ren 版 vis 网络：把本部件前方一格（虚构源位置）注册为外部源。
        // VisSourceRegistry 的 provider（见 ThEVisNetworkSetup）会把该位置上的请求
        // 委托回本部件（经由 P2P 隧道到面对中继的一端抽取）。
        BlockPos front = getBlockEntity().getBlockPos().relative(getSide());
        VisNetworkApi.registerSource(serverLevel, front);
        this.providerActive = true;
        this.isProvider = true;
    }

    private void removeProvider() {
        Level level = getLevel();
        if (level instanceof ServerLevel serverLevel && providerActive && getBlockEntity() != null) {
            BlockPos front = getBlockEntity().getBlockPos().relative(getSide());
            VisNetworkApi.removeSource(serverLevel, front);
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
        return new TickingRequest(TICK_RATE, TICK_RATE, false);
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

    // ===== 卸载时注销外部源 =====

    @Override
    public void removeFromWorld() {
        removeProvider();
        super.removeFromWorld();
    }
}
