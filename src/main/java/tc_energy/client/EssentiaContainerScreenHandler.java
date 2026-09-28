package tc_energy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.me.common.RepoSlot;

import tc_energy.ThaumicEnergistics;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.tc.ThEEssentiaContainers;
import tc_energy.common.network.EssentiaContainerInteractC2SPacket;

/**
 * 源质容器（罐子/安瓿）在 ME 终端网格上的交互拦截（无 mixin，NeoForge ScreenEvent）。
 * <p>
 * 全接管矩阵（手持罐子/安瓿时右键网格一律走自有包，不走 AE2 原生 EMPTY/FILL——
 * 原生链路的单次量绑定 getAmountPerUnit（显示因子=1），与 Ren 容器"瓶整 8/罐整 64"语义不符）：
 * <ul>
 *   <li>满容器 右键网格任意处 → DEPOSIT 整组=false（存入手中 1 个容器全部源质，容器留空）</li>
 *   <li>满容器 Shift+右键网格 → DEPOSIT 整组=true（存入手中整组容器）</li>
 *   <li>空容器 右键网格源质条目 → FILL 整组=false（取出一整瓶 8 / 一整罐 64）</li>
 *   <li>空容器 Shift+右键网格源质条目 → FILL 整组=true（装满手中整组）</li>
 *   <li>空容器 右键网格非源质条目 → 拦截（防容器物品被放进网格）</li>
 *   <li>左键 → 不拦截（容器物品本身入网络，AE2 原生）</li>
 * </ul>
 * 背包格 Shift+右键不拦截：GuiEssentiaTerminal 自有"存入背包格"链路照常工作。
 * <p>
 * <b>接管范围</b>：任意 {@code AbstractContainerScreen} + 菜单是 {@code MEStorageMenu}
 * —— 覆盖 ThE 源质终端 / 奥术合成终端、AE2 原生 ME 终端，以及第三方复用 AE2 终端菜单的
 * 屏幕（如 WCWT「ME 综合工作终端」）。若只判 {@code MEStorageScreen}，第三方屏幕会漏管，
 * 导致 AE2 原生链路与本类同时生效（一次右键消耗 2 个容器）。
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class EssentiaContainerScreenHandler {

    private EssentiaContainerScreenHandler() {}

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        // ⚠ 只判 MEStorageScreen 会漏掉第三方复用 AE2 终端菜单的屏幕
        //   （如 WCWT「ME 综合工作终端」里的奥术合成终端：菜单是 MEStorageMenu 子类，
        //    但屏幕不是 MEStorageScreen）。漏管时 AE2 原生右键链路会同时生效 →
        //    一次右键消耗 2 个容器：1 个被本 mod 的包倒空源质、1 个被 AE2 当普通物品存入网络。
        //    因此改为「任意 AbstractContainerScreen + 菜单是 MEStorageMenu」。
        if (!(event.getScreen()
                instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            return;
        }
        if (event.getButton() != 1) {
            return; // 只处理右键；左键 = 容器物品本身入网络（AE2 原生）
        }
        if (!(screen.getMenu() instanceof appeng.menu.me.common.MEStorageMenu menu)) {
            return; // 只接管 AE2 ME 终端类菜单
        }
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            return;
        }
        // 权威手持来源：carried（拾取态）优先，其次主手
        ItemStack hand = !menu.getCarried().isEmpty() ? menu.getCarried() : player.getMainHandItem();
        if (hand.isEmpty() || !ThEEssentiaContainers.isEssentiaContainer(hand)) {
            return;
        }

        // 点击位置分类：网格条目（RepoSlot）/ 其他菜单槽（背包格等）
        net.minecraft.world.inventory.Slot slot = screen.getSlotUnderMouse();
        boolean overGridEntry = false;
        appeng.api.stacks.AEKey gridKey = null;
        if (slot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            if (entry != null) {
                overGridEntry = true;
                gridKey = entry.getWhat();
            }
        }

        boolean shift = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.hasShiftDown();

        // ===== 右键网格一律自有包接管（单容器 + 整组统一语义，不依赖 AE2 原生 EMPTY/FILL）=====
        if (overGridEntry || ThEEssentiaContainers.hasEssentia(hand)) {
            event.setCanceled(true);
            boolean wholeGroup = shift;
            if (ThEEssentiaContainers.hasEssentia(hand)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        EssentiaContainerInteractC2SPacket.deposit(menu.containerId, wholeGroup, hand));
            } else if (gridKey instanceof AEssentiaKey key) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        EssentiaContainerInteractC2SPacket.fill(menu.containerId, wholeGroup, key.getId(), hand));
            } else {
                // 空容器右键网格空白处：无目标源质，拦截但不动作
            }
            // ⚠ 本地立即清空 carried 中的源质容器：万一本屏幕未被完全接管（第三方终端），
            //   AE2 原生右键链路还会把 carried 的容器"当普通物品存进 ME 网络"
            //   （mouseClicked → 容器交互 fallthrough → SPLIT_OR_PLACE_SINGLE），
            //   造成一次右键消耗 2 个容器。服务端处理完会以权威状态重新同步 carried。
            //   （旧 MixinMEStorageScreen 版本正是靠这一步规避，迁移到 ScreenEvent 时丢失了。）
            clearCarriedContainer(menu);
        } else if (overGridEntryNotEssentia(slot)) {
            // 空容器右键网格非源质条目：拦截，防容器物品被放进网格
            event.setCanceled(true);
        }
        // 其余情况（空容器右键背包格/空白）放行原生
    }

    /** carried 中是源质容器时本地清空（服务端随后以权威状态同步回来）。 */
    private static void clearCarriedContainer(appeng.menu.me.common.MEStorageMenu menu) {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && ThEEssentiaContainers.isEssentiaContainer(carried)) {
            menu.setCarried(ItemStack.EMPTY);
        }
    }

    private static boolean overGridEntryNotEssentia(net.minecraft.world.inventory.Slot slot) {
        if (slot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            return entry != null && !(entry.getWhat() instanceof AEssentiaKey);
        }
        return false;
    }
}
