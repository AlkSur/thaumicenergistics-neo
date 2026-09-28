package tc_energy.common.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.entity.ThaumcraftGolemEntity;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.common.network.GolemBackpackSyncPacket;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 傀儡背包无线网络交互的 Tick 驱动 AI。
 * 按 1.7.10 的 AI 体系，根据傀儡核心类型执行不同 AE 网络操作：
 * FILL(0)/GATHER(2) 物品、LIQUID(5) 流体、ESSENTIA(6) 源质。
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class GolemBackpackTickHandler {

    /**
     * 网络操作冷却时间（tick），与 1.7.10 {@code AIAENetworkGolem.NETWORK_COOLDOWN} 一致。
     */
    private static final int NETWORK_COOLDOWN = 20;

    /**
     * 同步状态冷却时间（tick），与 1.7.10 的 18 tick 一致。
     */
    private static final int SYNC_COOLDOWN = 18;

    private GolemBackpackTickHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ThaumcraftGolemEntity golem)) {
            return;
        }

        if (!(golem.level() instanceof ServerLevel)) {
            return;
        }

        if (!GolemBackpackHandler.hasBackpack(golem)) {
            return;
        }

        GlobalPos linkTarget = GolemBackpackHandler.getBackpackLink(golem);
        if (linkTarget == null) {
            return;
        }

        // 同步背包连接状态到客户端（每 SYNC_COOLDOWN tick 一次）
        syncBackpackStatus(golem, linkTarget);

        // 冷却检查：使用傀儡的 persistentData 存储冷却计时器
        int cooldown = golem.getPersistentData().getInt("ThEWifiCooldown");
        if (cooldown > 0) {
            golem.getPersistentData().putInt("ThEWifiCooldown", cooldown - 1);
            return;
        }

        golem.getPersistentData().putInt("ThEWifiCooldown", NETWORK_COOLDOWN);

        WirelessAELinkGolem link = new WirelessAELinkGolem(golem, linkTarget);
        if (!link.isConnected()) {
            return;
        }

        int maxRate = WirelessAELinkGolem.getMaxItemRate(golem);

        var core = golem.coreId();
        if (core == null) {
            return;
        }
        if (core == dev.tc4port.thaumcraft.common.GolemCoreId.FILL) {
            doFill(golem, link, maxRate);
        } else if (core == dev.tc4port.thaumcraft.common.GolemCoreId.GATHER) {
            doGather(golem, link, maxRate);
        } else if (core == dev.tc4port.thaumcraft.common.GolemCoreId.LIQUID) {
            doLiquid(golem, link);
        } else if (core == dev.tc4port.thaumcraft.common.GolemCoreId.ALCHEMY) {
            doEssentia(golem, link);
        }
    }

    /**
     * GATHER 核心 (ID=2): 傀儡手持物品时存入 AE 网络（对应 1.7.10 AIGolemWifiGather）。
     */
    private static void doGather(ThaumcraftGolemEntity golem, WirelessAELinkGolem link, int maxRate) {
        ItemStack held = golem.getCarried();
        if (held.isEmpty()) {
            return;
        }

        link.depositStack(held, maxRate);

        if (held.isEmpty()) {
            golem.setCarried(ItemStack.EMPTY);
            golem.startActionTimer();
        }
    }

    /**
     * FILL 核心 (ID=0): 从 AE 网络提取物品给傀儡，用于填充 home 容器（对应 1.7.10 AIGolemWifiFill）。
     * 与 TC 原版 workFill() 的区别：原版从附近标记容器取物，无线背包版直接从 ME 网络取。
     */
    private static void doFill(ThaumcraftGolemEntity golem, WirelessAELinkGolem link, int maxRate) {
        if (!golem.getCarried().isEmpty()) {
            return;
        }

        Container homeContainer = getHomeContainer(golem);
        if (homeContainer == null) {
            return;
        }

        ItemStack filterToExtract = null;
        int extractAmount = 0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack filter = ItemStack.EMPTY /* Ren golem 无过滤器位：功能暂缺 */;
            if (filter.isEmpty()) {
                continue;
            }

            int desired = Math.max(1, filter.getCount());
            int current = countInContainer(homeContainer, filter);
            int needed = desired - current;

            if (needed <= 0) {
                continue;
            }

            if (!hasRoom(homeContainer, filter, golem.getHomeFace())) {
                continue;
            }

            filterToExtract = filter.copyWithCount(1);
            extractAmount = Math.min(needed, golem.getCarryLimit());
            break;
        }

        if (filterToExtract == null) {
            return;
        }

        ItemStack request = filterToExtract.copyWithCount(extractAmount);
        ItemStack extracted = link.extractStack(request, maxRate);
        if (extracted != null && !extracted.isEmpty()) {
            golem.setCarried(extracted);
            golem.startActionTimer();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取傀儡 home 位置的容器（对应 TC 的 homeContainer()）。
     */
    @Nullable
    private static Container getHomeContainer(ThaumcraftGolemEntity golem) {
        BlockPos home = golem.getRestrictCenter();
        BlockPos apparatusPos = home.relative(golem.getHomeFace().getOpposite());

        Container container = containerAt(golem.level(), apparatusPos);
        if (container != null) {
            return container;
        }

        return containerAt(golem.level(), home);
    }

    @Nullable
    private static Container containerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container container) {
            return container;
        }
        return null;
    }

    /**
     * 计算容器中匹配物品的数量。
     * 对应 TC 的 {@code count()} 方法。
     */
    private static int countInContainer(Container container, ItemStack match) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, match)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 检查容器是否还有空间放入指定物品（对应 TC 的 hasRoom()）。
     */
    private static boolean hasRoom(Container container, ItemStack stack, Direction side) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    // ==================== 网络同步 ====================

    /**
     * 每 SYNC_COOLDOWN tick 发送背包连接状态 S2C 包（替代 1.7.10 DataWatcher 同步）。
     */
    private static void syncBackpackStatus(ThaumcraftGolemEntity golem, GlobalPos linkTarget) {
        int syncCooldown = golem.getPersistentData().getInt("ThEWifiSyncCooldown");
        if (syncCooldown > 0) {
            golem.getPersistentData().putInt("ThEWifiSyncCooldown", syncCooldown - 1);
            return;
        }
        golem.getPersistentData().putInt("ThEWifiSyncCooldown", SYNC_COOLDOWN);

        WirelessAELinkGolem link = new WirelessAELinkGolem(golem, linkTarget);
        boolean inRange = link.isConnected();
        int status = inRange
                ? GolemBackpackSyncPacket.STATUS_IN_RANGE
                : GolemBackpackSyncPacket.STATUS_OUT_OF_RANGE;

        BackpackSkins skin = GolemBackpackHandler.getBackpackSkin(golem);

        GolemBackpackSyncPacket packet = new GolemBackpackSyncPacket(
                golem.getId(), status, skin.ordinal());
        PacketDistributor.sendToPlayersTrackingEntity(golem, packet);
    }

    // ==================== LIQUID 核心 (ID=5) ====================

    /**
     * LIQUID 核心 (ID=5): 从 AE 网络提取流体给傀儡（对应 1.7.10 AIGolemWifiLiquid）。
     */
    private static void doLiquid(ThaumcraftGolemEntity golem, WirelessAELinkGolem link) {
        FluidStack carried = golem.getFluidCarried();
        if (!carried.isEmpty()) {
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack filter = ItemStack.EMPTY /* Ren golem 无过滤器位：功能暂缺 */;
            if (filter.isEmpty()) continue;

            var fluidHandler = filter.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
            if (fluidHandler == null) continue;

            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                FluidStack fluid = fluidHandler.getFluidInTank(tank);
                if (fluid.isEmpty()) continue;

                int maxRate = WirelessAELinkGolem.getMaxFluidRate(golem);
                int requestAmount = Math.min(golem.getFluidCarryLimit(), maxRate);
                FluidStack request = fluid.copyWithAmount(requestAmount);
                FluidStack extracted = link.extractFluid(request, maxRate);

                if (extracted != null && !extracted.isEmpty()) {
                    golem.setFluidCarried(extracted);
                    golem.startActionTimer();
                    return;
                }
            }
        }
    }

    // ==================== ESSENTIA 核心 (ID=6) ====================

    /**
     * ESSENTIA 核心 (ID=6): 傀儡与 AE 源质网络交互（对应 1.7.10 AIGolemWifiEssentia）。
     */
    private static void doEssentia(ThaumcraftGolemEntity golem, WirelessAELinkGolem link) {
        AspectId aspect = golem.getEssentiaCarried();
        int amount = golem.getEssentiaAmount();

        if (aspect != null && amount > 0) {
            net.minecraft.resources.ResourceLocation aspectId = aspect.value();
            int deposited = link.depositEssentia(aspectId, amount);
            if (deposited > 0) {
                int remaining = amount - deposited;
                golem.setEssentiaCarried(remaining > 0 ? aspect : null, remaining);
                golem.startActionTimer();
            }
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack filter = ItemStack.EMPTY /* Ren golem 无过滤器位：功能暂缺 */;
            if (filter.isEmpty()) continue;

            if (dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi.capacity(filter) > 0) {
                var aspectList = dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi.contents(filter);
                if (aspectList.amounts().isEmpty()) continue;

                for (var filterEntry : aspectList.amounts().entrySet()) {
                    dev.tc4port.thaumcraft.api.aspect.AspectId filterAspect = filterEntry.getKey();
                    int requestAmount = Math.min(golem.getCarryLimit(), WirelessAELinkGolem.getMaxEssentiaRate(golem));
                    int extracted = link.extractEssentia(filterAspect.value(), requestAmount);
                    if (extracted > 0) {
                        golem.setEssentiaCarried(filterAspect, extracted);
                        golem.startActionTimer();
                        return;
                    }
                }
            }
        }
    }
}
