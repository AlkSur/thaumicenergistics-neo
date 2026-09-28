package tc_energy.common.features;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import dev.tc4port.thaumcraft.research.ResearchCatalog;
import dev.tc4port.thaumcraft.research.ResearchEntryDefinition;
import dev.tc4port.thaumcraft.research.ResearchPageDefinition;
import tc_energy.ThaumicEnergistics;

/**
 * ThE 配方注册（Ren 版 TC4）：配方本体由 data pack 提供（data/tc_energy/recipe/...）。
 * <p>
 * 本类额外承担：服务端启动后自检——研究页引用的全部 recipe_ids 是否能被 RecipeManager 解析，
 * 缺失的逐条打 WARN（手册里“这份旧版配方尚未完成重建”即对应解析失败的引用）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class RecipeRegistration {
    private RecipeRegistration() {}

    public static void registerAll() {
        ThaumicEnergistics.LOG.info("ThaumicEnergistics recipes provided by data pack (data/tc_energy/recipe).");
    }

    /** 服务端启动后:校验研究页全部 recipe_ids 的运行时可达性。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        selfCheckRecipes(event.getServer());
    }

    private static void selfCheckRecipes(MinecraftServer server) {
        try {
            RecipeManager manager = server.getRecipeManager();
            int total = 0;
            int missing = 0;
            for (ResearchEntryDefinition entry : ResearchCatalog.entries().values()) {
                for (ResearchPageDefinition page : entry.pages()) {
                    List<ResourceLocation> ids = page.recipeIds();
                    for (ResourceLocation id : ids) {
                        if (!id.getNamespace().equals(ThaumicEnergistics.MODID)) {
                            continue;
                        }
                        total++;
                        if (manager.byKey(id).isEmpty()) {
                            missing++;
                            ThaumicEnergistics.LOG.warn("[ThE-Recipes] research page references UNRESOLVABLE recipe id: {} (entry {}) - manual will show recipe_unavailable",
                                    id, entry.key());
                        }
                    }
                }
            }
            ThaumicEnergistics.LOG.info("[ThE-Recipes] self-check done: {} recipe refs checked, {} unresolvable", total, missing);
        } catch (Exception e) {
            ThaumicEnergistics.LOG.warn("[ThE-Recipes] self-check failed: {}", e.toString());
        }
    }
}
