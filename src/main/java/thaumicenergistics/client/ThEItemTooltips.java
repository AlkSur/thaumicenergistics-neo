package thaumicenergistics.client;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.inventory.HandlerKnowledgeCore;
import thaumicenergistics.init.ModItems;

/**
 * One line of purpose for TE's machines (the research summary they already have), and what a knowledge
 * core holds (TEU-14).
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class ThEItemTooltips {
    /** Item path → research key whose "tc.research_text.<KEY>" summary describes it. */
    private static final Map<String, String> SUMMARIES = Map.ofEntries(
            Map.entry("essentia_provider", "ESSENTIAPROVIDER"),
            Map.entry("essentia_provider_connection", "ESSENTIAPROVIDER"),
            Map.entry("infusion_provider", "INFUSIONPROVIDER"),
            Map.entry("arcane_assembler", "ARCANEASSEMBLER"),
            Map.entry("essentia_vibration_chamber", "ESSENTIAVIBRATIONCHAMBER"),
            Map.entry("knowledge_inscriber", "KNOWLEDGEINSCRIBER"),
            Map.entry("distillation_encoder", "DISTILLATIONENCODER"),
            Map.entry("infusion_monitor", "INFUSIONMONITOR"),
            Map.entry("arcane_crafting_terminal", "ARCANECRAFTINGTERMINAL"),
            Map.entry("vis_interface", "VISINTERFACE"),
            Map.entry("golem_wifi_backpack", "GOLEMWIFIBACKPACK"),
            Map.entry("brain_of_probability", "GACHABOX"));

    private ThEItemTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!ThaumicEnergistics.MODID.equals(id.getNamespace())) return;
        var tooltip = event.getToolTip();
        if (stack.is(ModItems.KNOWLEDGE_CORE.get())) {
            var core = new HandlerKnowledgeCore(stack);
            tooltip.add(Component.translatable("thaumicenergistics.tooltip.knowledge_core.count",
                    core.getStoredEntryCount(), HandlerKnowledgeCore.MAXIMUM_STORED_PATTERNS)
                    .withStyle(ChatFormatting.GRAY));
            var outputs = core.getStoredOutputs();
            for (int i = 0; i < Math.min(5, outputs.size()); i++) {
                tooltip.add(Component.literal("  ").append(outputs.get(i).getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (outputs.size() > 5) {
                tooltip.add(Component.translatable("thaumicenergistics.tooltip.more", outputs.size() - 5)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        if (stack.is(ModItems.WIRELESS_CONNECTOR.get())) {
            tooltip.add(Component.translatable("thaumicenergistics.tooltip.wireless_connector").withStyle(ChatFormatting.GRAY));
            return;
        }
        String research = SUMMARIES.get(id.getPath());
        if (research != null) {
            tooltip.add(Component.translatable("tc.research_text." + research).withStyle(ChatFormatting.GRAY));
        }
    }
}
