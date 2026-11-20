package thaumicenergistics.common.integration.appeng;

import appeng.api.integrations.igtooltip.PartTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.common.parts.VisInterfacePart;

/**
 * Status lines for TE parts in Jade/WTHIT through AE2's own part tooltip API (TEU-05): whether a vis
 * interface faces a relay and has vis behind it, and whether a wired arcane terminal has a vis source.
 */
public final class ThEPartTooltips {
    private static final String RELAY = "thaumicenergistics:Relay";
    private static final String AVAILABLE = "thaumicenergistics:VisAvailable";
    private static final String SOURCE = "thaumicenergistics:VisSource";

    private ThEPartTooltips() {
    }

    public static void register() {
        PartTooltips.addServerData(VisInterfacePart.class, (player, part, data) -> {
            data.putBoolean(RELAY, part.facesRelay());
            data.putBoolean(AVAILABLE, part.hasAvailableRelay());
        });
        PartTooltips.addBody(VisInterfacePart.class, (part, context, builder) -> {
            var data = context.serverData();
            if (!data.contains(RELAY)) return;
            if (data.getBoolean(RELAY)) {
                builder.addLine(Component.translatable("thaumicenergistics.jade.vis_interface.relay")
                        .withStyle(ChatFormatting.GREEN));
            }
            builder.addLine(Component.translatable(data.getBoolean(AVAILABLE)
                    ? "thaumicenergistics.jade.vis_interface.available"
                    : "thaumicenergistics.jade.vis_interface.unavailable")
                    .withStyle(data.getBoolean(AVAILABLE) ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        });
        PartTooltips.addServerData(ArcaneCraftingTerminalPart.class,
                (player, part, data) -> data.putInt(SOURCE, part.visSourceState()));
        PartTooltips.addBody(ArcaneCraftingTerminalPart.class, (part, context, builder) -> {
            var data = context.serverData();
            if (!data.contains(SOURCE)) return;
            int state = data.getInt(SOURCE);
            builder.addLine(Component.translatable("thaumicenergistics.jade.assembler.vis_source",
                    Component.translatable(switch (state) {
                        case ArcaneCraftingTerminalPart.VIS_SOURCE_OK -> "thaumicenergistics.jade.assembler.vis_connected";
                        case ArcaneCraftingTerminalPart.VIS_SOURCE_UNAVAILABLE -> "thaumicenergistics.jade.assembler.vis_unavailable";
                        default -> "thaumicenergistics.jade.assembler.vis_disconnected";
                    })).withStyle(state == ArcaneCraftingTerminalPart.VIS_SOURCE_OK ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        });
    }
}
