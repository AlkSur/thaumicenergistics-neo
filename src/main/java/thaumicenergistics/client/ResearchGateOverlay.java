package thaumicenergistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftErrorScreen;
import appeng.menu.me.crafting.CraftConfirmMenu;

import thaumicenergistics.ThaumicEnergistics;

/**
 * 客户端：研究未解锁拦截的提示渲染。
 *
 * 服务端拦截合成提交后，通过 ResearchGateErrorPacket 发送研究键；
 * 本类在合成屏（确认屏/错误屏）之上绘制提示文字（最上层，不被 GUI 遮挡），
 * 并暴露 hasResearchGateError() 供 MixinCraftConfirmScreen 判断是否拦截 CPU 忙错误屏。
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class ResearchGateOverlay {

    private static String pendingResearch = null;

    private ResearchGateOverlay() {}

    /** 网络包处理：记录研究键（客户端线程）。 */
    public static void setPendingResearch(String research) {
        pendingResearch = research;
    }

    /**
     * 当前菜单是否处于研究未解锁拦截状态。
     * 依据 CraftConfirmMenu.submitError 判断：错误结果存在且 errorCode 非空，
     * 说明本次提交被拒绝（研究拦截触发 CPU_BUSY）。
     */
    public static boolean hasResearchGateError() {
        return pendingResearch != null;
    }

    /** 清空研究拦截状态（玩家离开合成屏或成功重试时由外部调用）。 */
    public static void clearPendingResearch() {
        pendingResearch = null;
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        // 玩家已离开合成屏（不是确认屏/错误屏）→ 清除拦截状态
        if (!(event.getScreen() instanceof CraftConfirmScreen)
                && !(event.getScreen() instanceof CraftErrorScreen)) {
            if (pendingResearch != null) {
                clearPendingResearch();
            }
            return;
        }
        // 无拦截状态不渲染
        if (pendingResearch == null) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();

        // 纯色无样式，保证与 MC 默认 GUI 字体一致
        Component msg = Component.literal("当前研究节点未解锁：" + pendingResearch);

        int textWidth = mc.font.width(msg);
        int x = (width - textWidth) / 2;
        int y = 30;

        drawWithShadow(gui, mc, msg, x, y, 0xFF5555, 0x000000);
    }

    /** 以指定颜色绘制文字并叠 8 向同偏移描边，保证在全屏 GUI 之上可读。 */
    private static void drawWithShadow(GuiGraphics gui, Minecraft mc, Component msg, int x, int y,
                                       int textColor, int outlineColor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                gui.drawString(mc.font, msg, x + dx, y + dy, outlineColor);
            }
        }
        gui.drawString(mc.font, msg, x, y, textColor);
    }
}
