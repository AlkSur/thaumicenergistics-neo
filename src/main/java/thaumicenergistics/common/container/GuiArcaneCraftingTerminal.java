package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import appeng.menu.slot.AppEngCraftingSlot;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.gui.AspectGuiRenderer;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;

import java.util.Map;

import org.anti_ad.mc.ipn.api.IPNIgnore;

/**
 * 奥术合成终端 GUI。标 @IPNIgnore：IPN 一键整理完全跳过此界面（不渲染排序按钮、不执行整理）。
 */
@IPNIgnore
public class GuiArcaneCraftingTerminal extends MEStorageScreen<ContainerArcaneCraftingTerminal> {

    private static final int ICON_SIZE = 14;

    public GuiArcaneCraftingTerminal(ContainerArcaneCraftingTerminal menu, Inventory playerInventory,
                                     Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        if (!this.menu.hasValidRecipe) return;

        Map<String, Integer> aspectMap = this.menu.getParsedAspects();
        if (aspectMap.isEmpty()) return;

        // 锚点取合成结果槽的 Y：圆环紧贴结果槽左侧，窗口缩放时随槽位一起移动
        int anchorY = -1;
        for (var slot : this.menu.slots) {
            if (slot instanceof appeng.menu.slot.AppEngCraftingSlot) {
                anchorY = slot.y;
                break;
            }
        }
        if (anchorY < 0) anchorY = 95;

        // 两列三行：左列 x=10，右列 x=80；行偏移对应三行圆环
        int[] aspectOffsets = { -16, 1, 17 };

        int index = 0;
        for (Map.Entry<String, Integer> entry : aspectMap.entrySet()) {
            if (index >= 6) break;

            Aspect aspect = Aspect.getAspect(entry.getKey());
            if (aspect != null) {
                int col = index / 3; // 0=左列, 1=右列
                int row = index % 3; // 0~2

                int x = (col == 0) ? 10 : 80;
                int y = anchorY + aspectOffsets[row];

                AspectGuiRenderer.draw(guiGraphics, aspect, x, y, ICON_SIZE, 1.0f);

                String amountStr = String.valueOf(entry.getValue());
                int textX = x + ICON_SIZE - this.font.width(amountStr) + 1;
                int textY = y + ICON_SIZE - 6;
                guiGraphics.drawString(this.font, amountStr, textX, textY, 0xFFFFFF, true);

                index++;
            }
        }
    }
}
