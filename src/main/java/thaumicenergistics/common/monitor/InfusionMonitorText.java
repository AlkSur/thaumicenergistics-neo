package thaumicenergistics.common.monitor;

import net.minecraft.network.chat.Component;
import thaumicenergistics.common.tiles.TileInfusionMonitor.RiskData;

/** 注魔风险的显示文字与颜色。 */
public final class InfusionMonitorText {
    private InfusionMonitorText() { }

    /** 风险数据 → 气泡多行文本（2 行：注魔风险/不稳定度；不显示基础+祭坛细分，细分在聊天栏）。 */
    public static Component bubble(RiskData risk) {
        // 内边距必须用不间断空格（\u00A0）：普通空格在行尾会被字体布局裁剪，
        // 导致背景只包住左侧、右侧贴边。NBSP 有真实字形，两侧都保留。
        String pad = "\u00A0\u00A0"; // 左右内边距（NBSP）
        String tierWord = tierWord(risk.tier());

        Component line1 = Component.literal(pad + "注魔风险: " + tierWord + pad)
                .withStyle(style -> style.withColor(tierColor(risk.tier())));
        Component line2 = Component.literal(pad + "不稳定度: " + risk.instability() + pad)
                .withStyle(style -> style.withColor(0xFFFFFFFF));

        // 上下各留一个空行（垂直内边距）
        return Component.literal("\n")
                .append(line1).append("\n")
                .append(line2).append("\n");
    }

    /** 5 档文字（无星星）。 */
    public static String tierWord(int tier) {
        return switch (tier) {
            case 1 -> "极低";
            case 2 -> "低";
            case 3 -> "中";
            case 4 -> "高";
            case 5 -> "极高";
            default -> "?";
        };
    }

    /** 5 档描述（神秘时代风）。 */
    public static String tierDesc(int tier) {
        return switch (tier) {
            case 1 -> "灵光如镜，仪式安然";
            case 2 -> "灵光微澜，无碍仪式";
            case 3 -> "符文低鸣，灵光流转不宁";
            case 4 -> "裂隙于虚空中睁开，注视着祭坛";
            case 5 -> "咒波翻涌，裂隙将吞没一切";
            default -> "";
        };
    }

    /** 档位颜色：1 绿 → 5 红。 */
    public static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xFF55FF55;
            case 2 -> 0xFFAAFF55;
            case 3 -> 0xFFFFFF55;
            case 4 -> 0xFFFFAA55;
            default -> 0xFFFF5555;
        };
    }

}
