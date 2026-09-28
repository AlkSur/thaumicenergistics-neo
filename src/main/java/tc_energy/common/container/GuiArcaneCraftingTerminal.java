package tc_energy.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import appeng.api.config.ActionItems;
import appeng.client.gui.widgets.ActionButton;
import appeng.core.AEConfig;
import appeng.menu.slot.AppEngCraftingSlot;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.client.AspectGuiRender;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import net.neoforged.neoforge.network.PacketDistributor;
import tc_energy.common.network.ArcaneTerminalClearC2SPacket;

import java.util.Map;

import org.anti_ad.mc.ipn.api.IPNIgnore;

/**
 * 奥术合成终端 GUI。标 @IPNIgnore：IPN 一键整理完全跳过此界面（不渲染排序按钮、不执行整理）。
 */
@IPNIgnore
public class GuiArcaneCraftingTerminal extends MEStorageScreen<ContainerArcaneCraftingTerminal> {

    private static final int ICON_SIZE = 14;

    /**
     * 要素消耗数字的整体缩放系数。
     * 折后值常常是 "2.70" 这类 4 字符（1 vis = 100 centivis），原尺寸 4×6px 会顶出 14px 图标，
     * 所以统一缩到 0.5 倍再画在图标右下角。嫌大/嫌小改这一个常量即可。
     */
    private static final float AMOUNT_TEXT_SCALE = 0.5F;

    public GuiArcaneCraftingTerminal(ContainerArcaneCraftingTerminal menu, Inventory playerInventory,
                                     Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        // 一键存入 ME 网络（合成格清空）
        ActionButton clearBtn = new ActionButton(ActionItems.S_STASH, btn -> menu.clearCraftingGrid());
        clearBtn.setHalfSize(true);
        clearBtn.setDisableBackground(true);
        widgets.add("clearCraftingGrid", clearBtn);

        // 一键存入玩家背包（合成格清空）
        ActionButton clearToPlayerInvBtn = new ActionButton(ActionItems.S_STASH_TO_PLAYER_INV,
                btn -> menu.clearToPlayerInventory());
        clearToPlayerInvBtn.setHalfSize(true);
        clearToPlayerInvBtn.setDisableBackground(true);
        widgets.add("clearToPlayerInv", clearToPlayerInvBtn);

        // 打开终端时上报 AE2 设置"关闭时自动清理合成槽"——服务端在菜单 removed 时兜底清理
        sendClearOnCloseSetting();
    }

    /**
     * 主区（0~175）按中轴线 87.5 居中：AE2 终端 imageWidth=194（srcRect），默认 leftPos 按 194 居中会让主区偏左约 10px。
     * 覆写 leftPos 用 175 居中，右侧 176~194（view cells 区）作为附加延伸到右侧。
     * <p>
     * 关键时序：AEBaseScreen.init 先调 AbstractContainerScreen.init（按 imageWidth 算 leftPos），随后用该 leftPos
     * 一次性解析并冻结按钮/文本层坐标（populateScreen）；而渲染时贴图与槽位按当前 leftPos 画。若把 175 居中放在
     * super.init() 之后，按钮层基准是 194 居中、贴图层是 175 居中，差 (194-175)/2≈10px，按钮层就像整体左移。
     * 因此先把 imageWidth 临时改为 175，让 super.init() 全程内部就用 87.5 居中解析所有层，再恢复。
     */
    @Override
    public void init() {
        int originalImageWidth = this.imageWidth;
        this.imageWidth = 175;
        super.init();
        this.imageWidth = originalImageWidth;
        this.leftPos = (this.width - 175) / 2;
    }

    /** 向服务端上报当前 AE2 全局设置"关闭时自动清理合成槽"。 */
    private void sendClearOnCloseSetting() {
        boolean enabled = AEConfig.instance().isClearGridOnClose();
        PacketDistributor.sendToServer(new ArcaneTerminalClearC2SPacket(
                this.menu.containerId, ArcaneTerminalClearC2SPacket.ACTION_SET_CLEAR_ON_CLOSE, enabled));
    }

    /**
     * 关闭终端时按 AE2 全局设置"自动清理合成槽"清空合成网格。
     * 双保险：先上报最新开关值，若开启再立即清一次（removed 兜底仍会执行，网格已空则无副作用）。
     */
    @Override
    public void onClose() {
        sendClearOnCloseSetting();
        if (AEConfig.instance().isClearGridOnClose()) {
            this.getMenu().clearCraftingGrid();
        }
        super.onClose();
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

            AspectId aspect = parseAspect(entry.getKey());
            if (aspect != null) {
                int col = index / 3; // 0=左列, 1=右列
                int row = index % 3; // 0~2

                int x = (col == 0) ? 10 : 80;
                int y = anchorY + aspectOffsets[row];

                AspectGuiRender.blit(guiGraphics, aspect, x, y, 1.0f);

                String amountStr = formatCentivis(entry.getValue());
                float scaledW = this.font.width(amountStr) * AMOUNT_TEXT_SCALE;
                float scaledH = this.font.lineHeight * AMOUNT_TEXT_SCALE;
                float textX = x + ICON_SIZE - scaledW + 1;
                // 相对图标底边再下移 2px（原来是 -1，现在 +1）
                float textY = y + ICON_SIZE - scaledH + 1;

                // 缩小字号（"缩小2号"）：用缩放矩阵画，尺寸不受字体本身限制
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(textX, textY, 0.0F);
                guiGraphics.pose().scale(AMOUNT_TEXT_SCALE, AMOUNT_TEXT_SCALE, 1.0F);
                guiGraphics.drawString(this.font, amountStr, 0, 0, 0xFFFFFF, true);
                guiGraphics.pose().popPose();

                index++;
            }
        }
    }

    /**
     * centivis → vis 文本，与 TC 奥术工作台 {@code ArcaneWorktableScreen#formatVis} 完全一致。
     * 菜单侧的 requiredAspectsData 存的就是「折后 centivis」，所以这里再除 100 显示。
     */
    private static String formatCentivis(int centivis) {
        return centivis % 100 == 0
                ? Integer.toString(centivis / 100)
                : String.format(java.util.Locale.ROOT, "%.2f", centivis / 100.0F);
    }

    private static AspectId parseAspect(String serialized) {
        try {
            return AspectId.parse(serialized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
