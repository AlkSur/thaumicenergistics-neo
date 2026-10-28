package thaumicenergistics.common.entities;

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
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.network.GolemBackpackSyncPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.Capabilities;
import thaumcraft.api.aspects.IEssentiaContainerItem;

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
            // 无背包：状态置位后补发一次 STATUS_NO_BACKPACK，清除客户端残留渲染
            var pd = golem.getPersistentData();
            if (!pd.getBoolean("ThEWifiNoBackpackSynced")) {
                pd.putBoolean("ThEWifiNoBackpackSynced", true);
                PacketDistributor.sendToPlayersTrackingEntity(golem,
                        new GolemBackpackSyncPacket(golem.getId(),
                                GolemBackpackSyncPacket.STATUS_NO_BACKPACK, 0));
            }
            return;
        }
        // 有背包：清除无背包标记，允许下次卸载后再同步
        golem.getPersistentData().remove("ThEWifiNoBackpackSynced");

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

        int core = golem.getCore();
        switch (core) {
            case 0 -> doFill(golem, link, maxRate);
            case 2 -> doGather(golem, link, maxRate);
            case 5 -> doLiquid(golem, link);
            case 6 -> doEssentia(golem, link);
            default -> {}
        }
    }

    /**
     * GATHER 核心 (ID=2): 傀儡手持物品时存入 AE 网络（对应 1.7.10 AIGolemWifiGather）。
     */
    private static void doGather(ThaumcraftGolemEntity golem, WirelessAELinkGolem link, int maxRate) {
        ItemStack held = golem.carried();
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
        if (!golem.carried().isEmpty()) {
            return;
        }

        Container homeContainer = getHomeContainer(golem);
        if (homeContainer == null) {
            return;
        }

        ItemStack filterToExtract = null;
        int extractAmount = 0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack filter = golem.getFilter(slot);
            if (filter.isEmpty()) {
                continue;
            }

            int desired = Math.max(1, filter.getCount());
            int current = countInContainer(homeContainer, filter);
            int needed = desired - current;

            if (needed <= 0) {
                continue;
            }

            if (!hasRoom(homeContainer, filter, golem.homeFacing())) {
                continue;
            }

            filterToExtract = filter.copyWithCount(1);
            extractAmount = Math.min(needed, golem.carryLimit());
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
        BlockPos home = golem.home();
        BlockPos apparatusPos = home.relative(golem.homeFacing().getOpposite());

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
        FluidStack carried = golem.fluidCarried();
        if (!carried.isEmpty()) {
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack filter = golem.getFilter(slot);
            if (filter.isEmpty()) continue;

            var fluidHandler = filter.getCapability(Capabilities.FluidHandler.ITEM);
            if (fluidHandler == null) continue;

            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                FluidStack fluid = fluidHandler.getFluidInTank(tank);
                if (fluid.isEmpty()) continue;

                int maxRate = WirelessAELinkGolem.getMaxFluidRate(golem);
                int requestAmount = Math.min(golem.fluidCarryLimit(), maxRate);
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
        Aspect aspect = golem.essentiaCarried();
        int amount = golem.essentiaAmount();

        if (aspect != null && amount > 0) {
            ResourceLocation aspectId = TCReflection.getAspectId(aspect);
            int deposited = link.depositEssentia(aspectId, amount);
            if (deposited > 0) {
                int remaining = amount - deposited;
                golem.setEssentiaCarried(remaining > 0 ? aspect : null, remaining);
                golem.startActionTimer();
            }
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack filter = golem.getFilter(slot);
            if (filter.isEmpty()) continue;

            if (filter.getItem() instanceof IEssentiaContainerItem containerItem) {
                AspectList aspectList = containerItem.getAspects(filter);
                if (aspectList == null || aspectList.size() == 0) continue;

                for (Aspect filterAspect : aspectList.getAspects()) {
                    ResourceLocation aspectId = TCReflection.getAspectId(filterAspect);
                    int requestAmount = Math.min(golem.carryLimit(), WirelessAELinkGolem.getMaxEssentiaRate(golem));
                    int extracted = link.extractEssentia(aspectId, requestAmount);
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
