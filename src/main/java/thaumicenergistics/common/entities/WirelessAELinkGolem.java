package thaumicenergistics.common.entities;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.util.Platform;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;

import java.util.Set;

/**
 * 傀儡与 AE2 无线网络的连接桥。
 * 提供物品/流体/源质的存取方法，供 {@link GolemBackpackTickHandler} 的 AI 逻辑调用。
 */
public class WirelessAELinkGolem {

    private final ThaumcraftGolemEntity golem;
    private final GlobalPos linkTarget;
    private IGrid cachedGrid;
    private IWirelessAccessPoint cachedAccessPoint;
    private IActionSource actionSource;
    private int tickCounter;

    public WirelessAELinkGolem(ThaumcraftGolemEntity golem, GlobalPos linkTarget) {
        this.golem = golem;
        this.linkTarget = linkTarget;
    }

    @Nullable
    public IGrid getGrid() {
        tickCounter++;
        if (tickCounter % 20 == 0 || cachedGrid == null) {
            updateConnection();
        }
        return cachedGrid;
    }

    public boolean isConnected() {
        return getGrid() != null;
    }

    // ==================== 物品网络操作 ====================

    /**
     * 获取 AE2 网络的物品存储（MEStorage 同时处理物品和流体）。
     */
    @Nullable
    public MEStorage getItemInventory() {
        IGrid grid = getGrid();
        if (grid == null) return null;
        return grid.getStorageService().getInventory();
    }

    @Nullable
    public IEnergyService getEnergyService() {
        IGrid grid = getGrid();
        if (grid == null) return null;
        return grid.getEnergyService();
    }

    /**
     * 将物品存入 AE 网络。
     * 使用 {@link StorageHelper#poweredInsert} 确保有足够能量时才存入。
     *
     * @param stack 要存入的物品（调用后 count 会被减少）
     * @param maxRate 每次操作最大存入数量
     */
    public void depositStack(ItemStack stack, int maxRate) {
        MEStorage storage = getItemInventory();
        if (storage == null) return;
        IEnergyService energy = getEnergyService();
        if (energy == null) return;
        if (actionSource == null) return;
        AEItemKey what = AEItemKey.of(stack);
        if (what == null) return;
        long depositSize = Math.min(stack.getCount(), maxRate);
        long inserted = StorageHelper.poweredInsert(energy, storage, what, depositSize, actionSource);
        stack.shrink((int) inserted);
    }

    /**
     * 从 AE 网络提取物品。
     * 使用 {@link StorageHelper#poweredExtraction} 确保有足够能量时才提取。
     *
     * @param target 要提取的物品模板（数量为期望数量）
     * @param maxRate 每次操作最大提取数量
     * @return 提取出的 ItemStack，可能为 null
     */
    @Nullable
    public ItemStack extractStack(ItemStack target, int maxRate) {
        MEStorage storage = getItemInventory();
        if (storage == null) return null;
        IEnergyService energy = getEnergyService();
        if (energy == null) return null;
        if (actionSource == null) return null;
        AEItemKey what = AEItemKey.of(target);
        if (what == null) return null;
        long requestSize = Math.min(target.getCount(), maxRate);
        long extracted = StorageHelper.poweredExtraction(energy, storage, what, requestSize, actionSource);
        if (extracted <= 0) return null;
        return what.toStack((int) extracted);
    }

    // ==================== 流体网络操作 ====================

    /**
     * 从 AE 网络提取流体。
     *
     * @param target 要提取的流体模板
     * @param maxRateMb 每次操作最大提取量（mB）
     * @return 提取出的 FluidStack，可能为 null
     */
    @Nullable
    public FluidStack extractFluid(FluidStack target, int maxRateMb) {
        MEStorage storage = getItemInventory();
        if (storage == null) return null;
        IEnergyService energy = getEnergyService();
        if (energy == null) return null;
        if (actionSource == null) return null;
        AEFluidKey what = AEFluidKey.of(target);
        if (what == null) return null;
        long requestSize = Math.min(target.getAmount(), maxRateMb);
        long extracted = StorageHelper.poweredExtraction(energy, storage, what, requestSize, actionSource);
        if (extracted <= 0) return null;
        return what.toStack((int) extracted);
    }

    /**
     * 将流体存入 AE 网络。
     *
     * @param fluid 流体栈（调用后 amount 会被减少）
     * @param maxRateMb 每次操作最大存入量（mB）
     */
    public void depositFluid(FluidStack fluid, int maxRateMb) {
        MEStorage storage = getItemInventory();
        if (storage == null) return;
        IEnergyService energy = getEnergyService();
        if (energy == null) return;
        if (actionSource == null) return;
        AEFluidKey what = AEFluidKey.of(fluid);
        if (what == null) return;
        long depositSize = Math.min(fluid.getAmount(), maxRateMb);
        long inserted = StorageHelper.poweredInsert(energy, storage, what, depositSize, actionSource);
        fluid.shrink((int) inserted);
    }

    // ==================== 源质网络操作 ====================

    @Nullable
    public thaumicenergistics.api.grid.IEssentiaGrid getEssentiaGrid() {
        IGrid grid = getGrid();
        if (grid == null) return null;
        return grid.getService(thaumicenergistics.api.grid.IEssentiaGrid.class);
    }

    /**
     * 从 AE 网络提取源质。
     *
     * @param aspectId 源质 ID（ResourceLocation，如 "thaumcraft:ignis"）
     * @param maxAmount 最大提取量
     * @return 实际提取的量
     */
    public int extractEssentia(net.minecraft.resources.ResourceLocation aspectId, int maxAmount) {
        thaumicenergistics.api.grid.IEssentiaGrid essentiaGrid = getEssentiaGrid();
        if (essentiaGrid == null) return 0;
        if (actionSource == null) return 0;

        int maxRate = getMaxEssentiaRate(golem);
        int toExtract = Math.min(maxAmount, maxRate);
        return (int) essentiaGrid.extractEssentia(aspectId, toExtract, false);
    }

    /**
     * 将源质存入 AE 网络。
     *
     * @param aspectId 源质 ID（ResourceLocation）
     * @param amount 要存入的量
     * @return 实际存入的量
     */
    public int depositEssentia(net.minecraft.resources.ResourceLocation aspectId, int amount) {
        thaumicenergistics.api.grid.IEssentiaGrid essentiaGrid = getEssentiaGrid();
        if (essentiaGrid == null) return 0;
        if (actionSource == null) return 0;

        int maxRate = getMaxEssentiaRate(golem);
        int toDeposit = Math.min(amount, maxRate);
        long rejected = essentiaGrid.injectEssentia(aspectId, toDeposit, false);
        return toDeposit - (int) rejected;
    }

    // ==================== 连接管理 ====================

    private void updateConnection() {
        cachedGrid = null;
        cachedAccessPoint = null;
        actionSource = null;

        Level level = golem.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var linkedLevel = serverLevel.getServer().getLevel(linkTarget.dimension());
        if (linkedLevel == null) {
            return;
        }

        var be = Platform.getTickingBlockEntity(linkedLevel, linkTarget.pos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            return;
        }

        IGrid grid = accessPoint.getGrid();
        if (grid == null) {
            return;
        }

        if (!isInRange(serverLevel, grid)) {
            return;
        }

        cachedGrid = grid;
        cachedAccessPoint = accessPoint;

        // 创建 ActionSource：使用 AP 作为 MachineSource
        if (accessPoint instanceof appeng.api.networking.security.IActionHost actionHost) {
            actionSource = IActionSource.ofMachine(actionHost);
        }
    }

    private boolean isInRange(ServerLevel level, IGrid grid) {
        Set<WirelessAccessPointBlockEntity> accessPoints = grid.getActiveMachines(WirelessAccessPointBlockEntity.class);
        if (accessPoints.isEmpty()) {
            return false;
        }

        double golemX = golem.getX();
        double golemY = golem.getY();
        double golemZ = golem.getZ();

        for (WirelessAccessPointBlockEntity ap : accessPoints) {
            if (!ap.isActive()) {
                continue;
            }

            var dc = ap.getLocation();
            if (dc.getLevel() != level) {
                continue;
            }

            double range = ap.getRange();
            double dx = dc.getPos().getX() - golemX;
            double dy = dc.getPos().getY() - golemY;
            double dz = dc.getPos().getZ() - golemZ;
            double sqDist = dx * dx + dy * dy + dz * dz;

            if (sqDist <= range * range) {
                return true;
            }
        }

        return false;
    }

    // ==================== 速率计算 ====================

    /**
     * 物品交互速率（按 1.7.10 AIAENetworkGolem 速率表）。
     * 基础: 8/24/32，高级傀儡 ×2
     */
    public static int getMaxItemRate(ThaumcraftGolemEntity golem) {
        int[] ITEM_RATES = {8, 24, 32};
        int rateMult = golem.isAdvanced() ? 2 : 1;
        int orderUpgrades = Math.max(0, Math.min(2, golem.getUpgradeAmount(2)));
        return ITEM_RATES[orderUpgrades] * rateMult;
    }

    /**
     * 流体交互速率（按 1.7.10 AIAENetworkGolem 速率表）。
     * 基础: 100/250/500 mB，高级傀儡 ×2
     */
    public static int getMaxFluidRate(ThaumcraftGolemEntity golem) {
        int[] FLUID_RATES = {100, 250, 500};
        int rateMult = golem.isAdvanced() ? 2 : 1;
        int orderUpgrades = Math.max(0, Math.min(2, golem.getUpgradeAmount(2)));
        return FLUID_RATES[orderUpgrades] * rateMult;
    }

    /**
     * 源质交互速率（按 1.7.10 AIAENetworkGolem 速率表）。
     * 基础: 4/12/16，高级傀儡 ×2
     */
    public static int getMaxEssentiaRate(ThaumcraftGolemEntity golem) {
        int[] ESS_RATES = {4, 12, 16};
        int rateMult = golem.isAdvanced() ? 2 : 1;
        int orderUpgrades = Math.max(0, Math.min(2, golem.getUpgradeAmount(2)));
        return ESS_RATES[orderUpgrades] * rateMult;
    }
}
