package thaumicenergistics.common.features;

import thaumicenergistics.ThaumicEnergistics;

/**
 * TE's Thaumcraft recipes are datapack files (data/thaumicenergistics/recipe/{arcane,crucible,infusion}), loaded
 * by Thaumcraft like its own, so packs and KubeJS can replace or remove them (TEL-03). What stays in code is the
 * aspect data of TE's items.
 */
public final class RecipeRegistration {
    private RecipeRegistration() {}

    public static void registerAll() {
        CrystalRecipeRegistration.registerAll();
        ThaumicEnergistics.LOG.info("ThaumicEnergistics item aspects registered; recipes come from data packs.");
    }
}
