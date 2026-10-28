package thaumicenergistics.client;

import appeng.client.gui.me.common.RepoSlot;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.EssentiaContainerItemStrategy;
import thaumicenergistics.common.integration.tc.ThEEssentiaContainers;
import thaumicenergistics.common.network.EssentiaTerminalDepositC2SPacket;
import thaumicenergistics.common.network.EssentiaTerminalFillC2SPacket;

/** 屏幕事件覆盖原版和第三方终端，鼠标在工具栏上时不抢占模式切换按钮。 */
public final class EssentiaTerminalInputHandler {
    private EssentiaTerminalInputHandler() {
    }

    @SubscribeEvent
    public static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1 || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof MEStorageMenu menu)) {
            return;
        }
        var hovered = screen.getSlotUnderMouse();
        if (hovered == null) return;
        boolean shift = AbstractContainerScreen.hasShiftDown();
        if (menu.isPlayerSideSlot(hovered)) {
            if (shift && ThEEssentiaContainers.isEssentiaContainer(hovered.getItem())) {
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromSlot(
                        menu.containerId, hovered.getSlotIndex(), hovered.getItem()));
                event.setCanceled(true);
            }
            return;
        }
        if (!(hovered instanceof RepoSlot repo)) return;
        var carried = menu.getCarried();
        if (EssentiaContainerItemStrategy.recoveredContent(carried) != null) {
            menu.handleInteraction(-1, InventoryAction.EMPTY_ENTIRE_ITEM);
            event.setCanceled(true);
            return;
        }
        var source = carried.isEmpty() ? menu.getPlayerInventory().player.getMainHandItem() : carried;
        if (!ThEEssentiaContainers.isEssentiaContainer(source)) return;
        if (ThEEssentiaContainers.hasEssentia(source)) {
            if (!carried.isEmpty()) {
                // carried 的标准抽空通过 AE2 原生协议和 ContainerItemStrategy 处理。
                menu.handleInteraction(-1, InventoryAction.EMPTY_ENTIRE_ITEM);
            } else {
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                        menu.containerId, source));
            }
        } else if (!shift && repo.getEntry() != null && repo.getEntry().getWhat() instanceof AEssentiaKey key) {
            PacketDistributor.sendToServer(EssentiaTerminalFillC2SPacket.create(menu.containerId, key.getId(), source));
        }
        event.setCanceled(true);
    }
}
