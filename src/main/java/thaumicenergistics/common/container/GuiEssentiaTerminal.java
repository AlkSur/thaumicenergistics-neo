package thaumicenergistics.common.container;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import thaumicenergistics.common.integration.tc.ThEEssentiaContainers;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;

import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.common.network.EssentiaTerminalDepositC2SPacket;
import thaumicenergistics.common.network.EssentiaTerminalFillC2SPacket;

/**
 * 源质终端 GUI：在 AE2 通用终端基础上扩展右键交互。
 * <ul>
 *   <li>Shift+右键玩家背包格：格内是源质容器 → 源质存入网络（空容器留原格）。</li>
 *   <li>右键玩家物品栏/背包槽 → 放行（AE2 原版物品交互）。</li>
 *   <li>右键网络区域（源质条目或空白），手持装有源质的罐子/安瓿 → 源质存入 ME 网络（空容器回背包）。</li>
 *   <li>手持空罐/空安瓿 + 右键源质条目 → 从网络取出源质装罐/装安瓿。</li>
 *   <li>左键仍是正常物品操作（不受影响）。</li>
 * </ul>
 */
public class GuiEssentiaTerminal extends MEStorageScreen<EssentiaTerminalMenu> {

    public GuiEssentiaTerminal(EssentiaTerminalMenu menu, Inventory playerInventory,
                               Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public boolean mouseClicked(double xCoord, double yCoord, int btn) {
        if (btn == 1) {
            var carried = this.menu.getCarried();
            boolean shiftDown = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.hasShiftDown();
            // 源质物品来源：优先菜单 carried；为空时回退玩家主手物品
            ItemStack sourceStack = !carried.isEmpty() ? carried
                    : this.menu.getPlayerInventory().player.getMainHandItem();
            boolean isContainer = ThEEssentiaContainers.isEssentiaContainer(sourceStack);
            boolean containerHasEssentia = isContainer && ThEEssentiaContainers.hasEssentia(sourceStack);

            // Shift+右键玩家物品栏/背包格：格内是源质容器 → 存入源质（空容器留原格）
            if (shiftDown && this.hoveredSlot != null && this.menu.isPlayerSideSlot(this.hoveredSlot)) {
                var slotStack = this.hoveredSlot.getItem();
                // 仅要素安瓿/要素罐子；天域之华等其余 IEssentiaContainerItem 一律放行
                if (ThEEssentiaContainers.isEssentiaContainer(slotStack)) {
                    PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromSlot(
                            this.menu.containerId, this.hoveredSlot.getSlotIndex(), slotStack));
                    return true;
                }
                // 非源质容器 → 放行（AE2 原版 Shift+右键快速移动）
                return super.mouseClicked(xCoord, yCoord, btn);
            }

            // 普通右键点击玩家物品栏/背包槽 → 放行（AE2 原版物品交互）
            if (!shiftDown && this.hoveredSlot != null && this.menu.isPlayerSideSlot(this.hoveredSlot)) {
                return super.mouseClicked(xCoord, yCoord, btn);
            }

            // Shift+右键：手持源质容器（满或空）→ 拦截。满→存入源质；空→阻止被当普通物品放入网络
            if (shiftDown && isContainer) {
                if (containerHasEssentia) {
                    this.menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                    PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                            this.menu.containerId, sourceStack));
                }
                return true;
            }

            // 普通右键网络区域：手持满容器 → 存入源质
            if (containerHasEssentia) {
                this.menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                        this.menu.containerId, sourceStack));
                return true;
            }

            // 手持空容器：右键源质条目 → 取出装罐；右键网络其他 → 拦截（防被放入网络）
            if (isContainer) {
                if (this.hoveredSlot instanceof appeng.client.gui.me.common.RepoSlot repoSlot) {
                    var entry = repoSlot.getEntry();
                    if (entry != null && entry.getWhat() instanceof thaumicenergistics.common.integration.appeng.AEssentiaKey key) {
                        PacketDistributor.sendToServer(EssentiaTerminalFillC2SPacket.create(
                                this.menu.containerId, key.getId(), sourceStack));
                        return true;
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(xCoord, yCoord, btn);
    }
}
