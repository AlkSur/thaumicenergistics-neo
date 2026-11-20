package thaumicenergistics.common.monitor;

import net.minecraft.network.chat.Component;
import thaumicenergistics.common.tiles.TileInfusionMonitor.RiskData;

/** 注魔风险的显示文字与颜色。 */
public final class InfusionMonitorText {
    private InfusionMonitorText() { }

    /** 风险数据 → 气泡多行文本（2 行：注魔风险/不稳定度；细分在聊天栏）。所有文字走语言键（TEC-15）。 */
    public static Component bubble(RiskData risk) {
        // 内边距必须用不间断空格（\u00A0）：普通空格在行尾会被字体布局裁剪。
        String pad = "\u00A0\u00A0";
        Component line1 = Component.literal(pad)
                .append(Component.translatable("thaumicenergistics.monitor.bubble.risk", tierWord(risk.tier())))
                .append(pad)
                .withStyle(style -> style.withColor(tierColor(risk.tier())));
        Component line2 = Component.literal(pad)
                .append(Component.translatable("thaumicenergistics.monitor.bubble.instability", risk.instability()))
                .append(pad)
                .withStyle(style -> style.withColor(0xFFFFFFFF));
        return Component.literal("\n").append(line1).append("\n").append(line2).append("\n");
    }

    /** 5 档文字（极低…极高）。 */
    public static Component tierWord(int tier) {
        return tier >= 1 && tier <= 5 ? Component.translatable("thaumicenergistics.jade.tier." + tier)
                : Component.literal("?");
    }

    /** 5 档描述（神秘时代风）。 */
    public static Component tierDesc(int tier) {
        return tier >= 1 && tier <= 5 ? Component.translatable("thaumicenergistics.monitor.tier_desc." + tier)
                : Component.empty();
    }

    /** 5 档后果说明（Jade）。 */
    public static Component tierEffect(int tier) {
        return tier >= 1 && tier <= 5 ? Component.translatable("thaumicenergistics.monitor.tier_effect." + tier)
                : Component.empty();
    }

    /**
     * Shortages travel as "tag=amount;tag=amount" and are rendered on the reading side, so each client shows
     * its own language and the aspect's real name (TET-18).
     */
    public static Component shortages(String encoded) {
        var result = Component.empty();
        boolean first = true;
        for (String part : encoded.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            var aspect = thaumcraft.api.aspects.Aspect.getAspect(part.substring(0, eq));
            Component name = aspect != null ? aspect.displayName() : Component.literal(part.substring(0, eq));
            if (!first) result.append(", ");
            result.append(Component.translatable("thaumicenergistics.monitor.shortage", name, part.substring(eq + 1)));
            first = false;
        }
        return result;
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
