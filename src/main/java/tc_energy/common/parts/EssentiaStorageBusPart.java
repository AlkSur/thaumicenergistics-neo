package tc_energy.common.parts;

import appeng.api.AECapabilities;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.IOBusPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.integration.appeng.AEssentiaKeyType;
import tc_energy.init.ModMenuTypes;

import java.util.Set;

/**
 * 源质存储总线：把相邻的源质容器（要素罐子 / 奥术蒸馏器 / 荒古炼金炉 / 源质管道）
 * 或相邻方块自身暴露的 {@code MEStorage}（例如子网接口）挂载为网络存储，
 * 使终端与子网能读写该容器。
 * <p>
 * 实现方式与 AE2 原版存储总线一致（{@link IStorageProvider#mountInventories}），
 * 不主动搬运源质。旧实现按输入总线逻辑把容器抽干再插入网络、且忽略插入返回值，
 * 导致网络塞不下时源质凭空消失——已移除。
 */
public class EssentiaStorageBusPart extends IOBusPart
        implements IStorageProvider, KeyTypeSelectionHost {

    @PartModels
    public static final ResourceLocation RL_BASE =
            ThaumicEnergistics.id("parts/essentia_storage_bus_base");
    @PartModels
    public static final ResourceLocation RL_OFF =
            ThaumicEnergistics.id("parts/essentia_storage_bus_off");
    @PartModels
    public static final ResourceLocation RL_HAS_CHANNEL =
            ThaumicEnergistics.id("parts/essentia_storage_bus_has_channel");
    @PartModels
    public static final ResourceLocation RL_ON =
            ThaumicEnergistics.id("parts/essentia_storage_bus_on");

    public static final PartModel MODEL_OFF = new PartModel(RL_BASE, RL_OFF);
    public static final PartModel MODEL_HAS_CHANNEL = new PartModel(RL_BASE, RL_HAS_CHANNEL);
    public static final PartModel MODEL_ON = new PartModel(RL_BASE, RL_ON);

    private static final double IDLE_POWER = 1.0;
    private boolean isWorkingFlag = false;

    /** 相邻源质容器的 MEStorage 视图（惰性创建，内部实时解析方块实体）。 */
    private EssentiaContainerStorage containerStorage;
    private Component storageDescription;

    private final KeyTypeSelection keyTypeSelection = new KeyTypeSelection(
            selection -> { },
            keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public EssentiaStorageBusPart(IPartItem<?> partItem) {
        super(TickRates.StorageBus, Set.of(AEssentiaKeyType.INSTANCE), partItem);
        this.getMainNode().setIdlePowerUsage(IDLE_POWER);
        // 注册为存储提供者，网络挂载时会调用 mountInventories（与 AE2 原版存储总线一致）
        this.getMainNode().addService(IStorageProvider.class, this);
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(5, 5, 12, 11, 11, 14);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(3, 3, 15, 13, 13, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        if (isWorkingFlag) return MODEL_ON;
        if (getMainNode() != null && getMainNode().isOnline()) return MODEL_HAS_CHANNEL;
        return MODEL_OFF;
    }

    /** 存储总线不做搬运工作：容器由 {@link #mountInventories} 挂载为网络存储。
     *  这里只做状态指示（相邻容器有源质 → 亮工作灯）。 */
    @Override
    protected boolean doBusWork(appeng.api.networking.IGrid grid) {
        BlockEntity adjacent = getAdjacentBlockEntity();
        this.isWorkingFlag = adjacent != null
                && tc_energy.common.integration.tc.TCReflection.peekEssentia(adjacent, 1) != null;
        return false;
    }

    // ===== IStorageProvider：把相邻存储挂载到网络 =====

    @Override
    public void mountInventories(IStorageMounts mounts) {
        BlockPos adjacentPos = getAdjacentPos();
        if (adjacentPos == null) return;
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.isLoaded(adjacentPos)) return;

        // 1) 相邻方块自身暴露 MEStorage（子网接口等）→ 直接挂载，使终端/子网可访问
        Direction side = this.getSide();
        MEStorage external = serverLevel.getCapability(AECapabilities.ME_STORAGE, adjacentPos,
                side != null ? side.getOpposite() : null);
        if (external != null) {
            mounts.mount(external, 0);
            return;
        }

        // 2) 否则把相邻源质容器（罐子/蒸馏器/荒古炼金炉/管道）包装成 MEStorage 挂载
        mounts.mount(getContainerStorage(), 0);
    }

    private EssentiaContainerStorage getContainerStorage() {
        if (containerStorage == null) {
            if (storageDescription == null) {
                storageDescription = Component.translatable("item.tc_energy.essentia_storage_bus");
            }
            containerStorage = new EssentiaContainerStorage(this::getAdjacentBlockEntity, storageDescription);
        }
        return containerStorage;
    }

    @Nullable
    private BlockPos getAdjacentPos() {
        Direction side = this.getSide();
        if (side == null || this.getBlockEntity() == null) return null;
        return this.getBlockEntity().getBlockPos().relative(side);
    }

    /** 实时解析相邻方块实体（容器可能被替换/移除，挂载视图每次调用重新读取）。 */
    @Nullable
    private BlockEntity getAdjacentBlockEntity() {
        BlockPos pos = getAdjacentPos();
        Level level = getLevel();
        if (pos == null || level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        IStorageProvider.requestUpdate(this.getMainNode());
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChanged(level, pos, neighbor);
        // 相邻方块变化 → 重新挂载（子网接口 ↔ 源质容器的切换）
        IStorageProvider.requestUpdate(this.getMainNode());
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State state) {
        super.onMainNodeStateChanged(state);
        IStorageProvider.requestUpdate(this.getMainNode());
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public void readFromNBT(CompoundTag extra, HolderLookup.Provider registries) {
        super.readFromNBT(extra, registries);
        this.keyTypeSelection.readFromNBT(extra, registries);
    }

    @Override
    public void writeToNBT(CompoundTag extra, HolderLookup.Provider registries) {
        super.writeToNBT(extra, registries);
        this.keyTypeSelection.writeToNBT(extra);
    }

    @Override
    protected MenuType<?> getMenuType() {
        return ModMenuTypes.ESSENTIA_STORAGE_BUS.get();
    }

    @Override
    public int getUpgradeSlots() { return 4; }
}
