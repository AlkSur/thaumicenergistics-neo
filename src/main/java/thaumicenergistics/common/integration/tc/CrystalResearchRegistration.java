package thaumicenergistics.common.integration.tc;

import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;
import thaumicenergistics.common.features.CrystalRecipeRegistration;
import thaumicenergistics.init.ModItems;

/** Crystal progression sits to the right of Basic Energistics; advanced matrices share cultivation research. */
final class CrystalResearchRegistration {
    private CrystalResearchRegistration() {}
    static void registerAll() {
        new ResearchItem("MYSTICCRYSTALGROWTH", ThEThaumcraft.TAB,
                new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 5).add(Aspect.EARTH, 3),
                2, 0, 2, new ItemStack(ModItems.MYSTIC_CRYSTAL.get()))
                .setRound().setParents("BASEENERGISTICS")
                .setPages(new ResearchPage("tc.research_text.MYSTICCRYSTALGROWTH.stage.1"),
                        new ResearchPage(CrystalRecipeRegistration.DAMAGED),
                        new ResearchPage(new InfusionRecipe[]{CrystalRecipeRegistration.CHIPPED,
                                CrystalRecipeRegistration.RESTORE_THAUMIUM}),
                        new ResearchPage(CrystalRecipeRegistration.FLAWED),
                        new ResearchPage(new InfusionRecipe[]{CrystalRecipeRegistration.FLAWLESS,
                                CrystalRecipeRegistration.RESTORE_VOID})).registerResearchItem();
        new ResearchItem("MYSTICCRYSTALCIRCUIT", ThEThaumcraft.TAB,
                new AspectList().add(Aspect.MIND, 5).add(Aspect.ORDER, 5).add(Aspect.MECHANISM, 3),
                4, 0, 2, new ItemStack(ModItems.MYSTIC_CIRCUIT_BOARD.get()))
                .setRound().setParents("MYSTICCRYSTALGROWTH")
                .setPages(new ResearchPage("tc.research_text.MYSTICCRYSTALCIRCUIT.stage.1"),
                        new ResearchPage(CrystalRecipeRegistration.PRESS), new ResearchPage(CrystalRecipeRegistration.DUST)).registerResearchItem();
        new ResearchItem("MYSTICPROCESSOR", ThEThaumcraft.TAB,
                new AspectList().add(Aspect.MIND, 5).add(Aspect.CRAFT, 5).add(Aspect.MECHANISM, 5),
                6, 0, 2, new ItemStack(ModItems.MYSTIC_PROCESSOR.get()))
                .setRound().setParents("MYSTICCRYSTALCIRCUIT")
                .setPages(new ResearchPage("tc.research_text.MYSTICPROCESSOR.stage.1")).registerResearchItem();
        new ResearchItem("THAUMICCRYSTALTOOLS", ThEThaumcraft.TAB,
                new AspectList().add(Aspect.TOOL, 5).add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 5),
                2, 2, 2, new ItemStack(ModItems.THAUMIC_CRYSTAL_PICKAXE.get()))
                .setRound().setParents("MYSTICCRYSTALGROWTH")
                .setPages(new ResearchPage("tc.research_text.THAUMICCRYSTALTOOLS.stage.1")).registerResearchItem();
    }
}
