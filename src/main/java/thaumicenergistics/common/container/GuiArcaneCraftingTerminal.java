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

public class GuiArcaneCraftingTerminal extends MEStorageScreen<ContainerArcaneCraftingTerminal> {

    /** 6 个要素图标位置（左上角坐标） */
    private static final int[][] ASPECT_POSITIONS = {
            {10, 95},   // 左列 第1行
            {10, 112},  // 左列 第2行
            {10, 128},  // 左列 第3行
            {80, 95},   // 右列 第1行
            {80, 112},  // 右列 第2行
            {80, 128},  // 右列 第3行
    };

    private static final int ICON_SIZE = 14;

    public GuiArcaneCraftingTerminal(ContainerArcaneCraftingTerminal menu, Inventory playerInventory,
                                     Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** 合成结果槽锚点 Y（首次计算缓存，避免每帧遍历 slots）。 */
    private int cachedAnchorY = -1;

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        if (!this.menu.hasValidRecipe) return;

        Map<String, Integer> aspectMap = this.menu.getParsedAspects();
        if (aspectMap.isEmpty()) return;

        // 获取合成结果槽的 Y 位置作为锚点（槽位置不变，首次计算后缓存，避免每帧遍历）
        int anchorY;
        if (cachedAnchorY < 0) {
            for (var slot : this.menu.slots) {
                // CRAFTING_RESULT 槽在 style JSON 中 bottom:140
                if (slot instanceof appeng.menu.slot.AppEngCraftingSlot) {
                    cachedAnchorY = slot.y;
                    break;
                }
            }
            // 若找不到锚点则回退到硬编码值
            if (cachedAnchorY < 0) cachedAnchorY = 95;
        }
        anchorY = cachedAnchorY;

        // 要素圆圈 Y = 锚点 + 偏移（-16/1/17，对应纹理 95/112/128）
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
