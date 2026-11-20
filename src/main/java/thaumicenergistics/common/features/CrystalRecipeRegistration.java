package thaumicenergistics.common.features;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModItems;

/** CE material progression, registered through neo's TC4 API rather than Thaumaturge JSON. */
/**
 * Aspects of TE's crystal items. The crystal recipes themselves are datapack files under
 * data/thaumicenergistics/recipe (TEL-03), so packs can change or remove them.
 */
public final class CrystalRecipeRegistration {
    private CrystalRecipeRegistration() {}

    public static void registerAll() {
        registerAspects();
    }

    private static AspectList growth(int amount) { return new AspectList().add(Aspect.CRYSTAL, amount).add(Aspect.MAGIC, amount); }
    private static ItemStack tc(String id) { return item("thaumcraft", id); }
    private static ItemStack item(String ns, String id) {
        var key = ResourceLocation.fromNamespaceAndPath(ns, id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing crystal recipe ingredient: " + key);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
    private static void aspect(ItemLike item, AspectList aspects) { ThaumcraftApi.registerObjectTag(new ItemStack(item), aspects); }
    private static void registerAspects() {
        aspect(ModItems.MYSTIC_CRYSTAL.get(), growth(2));
        aspect(ModItems.MYSTIC_CRYSTAL_DUST.get(), growth(1));
        aspect(ModItems.MYSTIC_CIRCUIT_BOARD.get(), growth(2).add(Aspect.ORDER, 1));
        aspect(ModItems.MYSTIC_PROCESSOR.get(), growth(2).add(Aspect.MECHANISM, 2).add(Aspect.MIND, 2));
        aspect(ModItems.THAUMIC_PROCESSOR_PRESS.get(), new AspectList().add(Aspect.METAL, 8).add(Aspect.CRAFT, 4).add(Aspect.MIND, 2));
        aspect(ModItems.THAUMIC_UPGRADE_TEMPLATE.get(), new AspectList().add(Aspect.MAGIC, 4).add(Aspect.CRAFT, 4));
        aspect(ModBlocks.MYSTIC_CRYSTAL_BLOCK.get(), growth(8));
        for (var block : List.of(ModBlocks.FLAWLESS_BUDDING_THAUMIC_CRYSTAL, ModBlocks.FLAWED_BUDDING_THAUMIC_CRYSTAL,
                ModBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL, ModBlocks.DAMAGED_BUDDING_THAUMIC_CRYSTAL)) aspect(block.get(), growth(8).add(Aspect.EARTH, 4));
        for (var block : List.of(ModBlocks.SMALL_MYSTIC_CRYSTAL_BUD, ModBlocks.MEDIUM_MYSTIC_CRYSTAL_BUD,
                ModBlocks.LARGE_MYSTIC_CRYSTAL_BUD, ModBlocks.MYSTIC_CRYSTAL_CLUSTER)) aspect(block.get(), growth(2));
        for (var tool : List.of(ModItems.THAUMIC_CRYSTAL_PICKAXE, ModItems.THAUMIC_CRYSTAL_AXE, ModItems.THAUMIC_CRYSTAL_SHOVEL,
                ModItems.THAUMIC_CRYSTAL_HOE, ModItems.THAUMIC_CRYSTAL_SWORD, ModItems.THAUMIC_CRYSTAL_WRENCH, ModItems.THAUMIC_CRYSTAL_KNIFE))
            aspect(tool.get(), growth(4).add(Aspect.TOOL, 4));
    }
}
