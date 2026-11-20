package thaumicenergistics.tests;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import java.math.BigInteger;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import thaumicenergistics.common.integration.neoecoae.ThENeoEcoOrders;
import thaumicenergistics.common.tiles.TileArcaneAssembler;

/** Neo ECOAE checks; loaded only when the mod is present (P2Checks guards the call). */
final class NeoEcoChecks {
    private NeoEcoChecks() { }

    static void run(GameTestHelper helper, TileArcaneAssembler assembler) {
        var contract = ECOParallelCraftingProviders.find(assembler);
        helper.assertTrue(contract != null, "ECO finds the assembler's parallel contract (E2)");
        helper.assertTrue(contract.eco$getAvailableParallelSlots() == assembler.parallelSlots(),
                "The contract reports the assembler's own slots");

        var goal = appeng.api.stacks.AEItemKey.of(Items.DIAMOND);
        var request = new ECOBigOrderRequest(goal, BigInteger.valueOf(1_000_000), false,
                new ECOPlannerOptions(false, false, Set.of()));
        request.submit(carrier -> {
            helper.assertTrue(carrier.patternTimes().isEmpty(), "A big order arrives as an empty carrier");
            helper.assertTrue(ThENeoEcoOrders.bigOrderKeys(carrier).contains(goal),
                    "The research gate sees what the big order crafts (TEN-02)");
            return null;
        });
    }

    /** E3: essentia matrices go through ECO's cell registry and store essentia, nothing else. */
    static void storage(GameTestHelper helper) {
        helper.assertTrue(thaumicenergistics.common.integration.neoecoae.NeoEcoItems.enabled(), "Matrices are registered");
        var stack = new net.minecraft.world.item.ItemStack(
                thaumicenergistics.common.integration.neoecoae.NeoEcoItems.MATRIX_16M.get());
        helper.assertTrue(cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.isCellHandled(stack), "ECO drives accept it");
        helper.assertTrue(stack.getItem() instanceof cn.dancingsnow.neoecoae.api.storage.INativeMEStorageRestrictedItem matrix
                && matrix.isNativeMEStorageRestricted(stack), "AE2's ME drive and chest refuse it");
        var cell = cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.getCellInventory(stack, null);
        helper.assertTrue(cell != null, "A cell inventory exists");
        helper.assertTrue(cell.getTier() == cn.dancingsnow.neoecoae.api.ECOTier.L4, "16M is an L4 matrix");
        helper.assertTrue(cell.getCellType() == thaumicenergistics.common.integration.neoecoae.ThENeoEcoStorage.ESSENTIA.get(),
                "Essentia has its own ECO cell type");
        helper.assertTrue(cell.getTotalBytes() == 1L << 24, "Same capacity as ECO's L4 matrices");
        var aer = thaumicenergistics.common.integration.appeng.AEssentiaKey.of(
                thaumicenergistics.common.integration.tc.AspectIds.getAspectId(thaumcraft.api.aspects.Aspect.AIR));
        var source = appeng.api.networking.security.IActionSource.empty();
        helper.assertTrue(cell.insert(aer, 1000, appeng.api.config.Actionable.MODULATE, source) == 1000, "Essentia goes in");
        helper.assertTrue(cell.insert(appeng.api.stacks.AEItemKey.of(Items.DIAMOND), 1,
                appeng.api.config.Actionable.MODULATE, source) == 0, "Items are refused");
        cell.persist();
        var reopened = cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.getCellInventory(stack, null);
        var counter = new appeng.api.stacks.KeyCounter();
        reopened.getAvailableStacks(counter);
        helper.assertTrue(counter.get(aer) == 1000, "Contents survive on the stack");
        helper.assertTrue(reopened.getStoredItemTypes() == 1 && reopened.getUsedBytes() > 1000, "Types cost bytes");
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {"eco_essentia_cell_housing", "eco_essentia_storage_cell_16m",
                "eco_essentia_storage_cell_64m", "eco_essentia_storage_cell_256m"}) {
            helper.assertTrue(recipes.byKey(thaumicenergistics.ThaumicEnergistics.id("neoecoae/" + id)).isPresent(),
                    "Recipe " + id + " loads with ECO");
        }
    }

    /** E8: the arcane crafting chamber offers its cores' recipes and takes a whole batch, paying vis for each. */
    static void chamber(GameTestHelper helper, thaumicenergistics.common.tiles.TileArcaneCraftingChamber chamber) {
        helper.assertTrue(chamber.getTier() == 4 && chamber.getCores().getContainerSize() == 9, "L4 holds 9 cores");
        helper.assertTrue(chamber.parallelSlots() == 32, "L4 takes batches of 32 (active=" + chamber.isNodeActive() + ", powered=" + chamber.isNodePowered() + ", capacity=" + chamber.getParallelCapacity() + ", crafting=" + chamber.isCrafting() + ")");
        var contract = ECOParallelCraftingProviders.find(chamber);
        helper.assertTrue(contract != null && contract.eco$getAvailableParallelSlots() == 32,
                "ECO sees the chamber's batch size through the assembler adapter");

        net.minecraft.world.item.ItemStack[] grid = new net.minecraft.world.item.ItemStack[9];
        java.util.Arrays.fill(grid, net.minecraft.world.item.ItemStack.EMPTY);
        grid[0] = new net.minecraft.world.item.ItemStack(Items.STONE);
        var recipe = new thaumicenergistics.common.integration.tc.ArcaneCraftingPattern(
                new thaumcraft.api.aspects.AspectList().add(thaumcraft.api.aspects.Aspect.AIR, 2),
                new net.minecraft.world.item.ItemStack(Items.DIAMOND), grid);
        var core = new net.minecraft.world.item.ItemStack(thaumicenergistics.init.ModItems.KNOWLEDGE_CORE.get());
        new thaumicenergistics.common.inventory.HandlerKnowledgeCore(core).addPattern(recipe);
        chamber.getCores().setItem(3, core);
        helper.assertTrue(chamber.getAvailablePatterns().size() == 1, "The chamber offers the recipe on its core");
        chamber.getCores().setItem(5, core.copy());
        helper.assertTrue(chamber.getAvailablePatterns().size() == 1, "The same recipe on two cores is offered once");

        var registries = helper.getLevel().registryAccess();
        var saved = chamber.saveWithFullMetadata(registries);
        var stored = new net.minecraft.nbt.CompoundTag();
        stored.putInt(thaumcraft.api.aspects.Aspect.AIR.getTag(), 5000);
        saved.put("StoredVis", stored);
        chamber.getCores().clearContent();
        chamber.loadWithComponents(saved, registries);
        helper.assertTrue(chamber.getCores().getItem(3).is(thaumicenergistics.init.ModItems.KNOWLEDGE_CORE.get())
                && chamber.getCores().getItem(5).is(thaumicenergistics.init.ModItems.KNOWLEDGE_CORE.get()), "Cores survive a reload");

        var pattern = chamber.getAvailablePatterns().getFirst();
        var stone = appeng.api.stacks.AEItemKey.of(Items.STONE);
        var total = new appeng.api.stacks.KeyCounter();
        total.add(stone, 32);
        helper.assertTrue(chamber.pushBatch(pattern, new appeng.api.stacks.KeyCounter[]{total}, 32), "A batch of 32 is taken");
        helper.assertTrue(chamber.getBatchSize() == 32, "The job is the whole batch");
        int perCraft = Math.round(2 * 10 * 0.8F);
        helper.assertTrue(chamber.requiredCvisForJob(thaumcraft.api.aspects.Aspect.AIR) == perCraft * 32,
                "Vis is paid for every craft in the batch");
        helper.assertTrue(chamber.parallelSlots() == 0, "A busy chamber offers no slots");
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {"arcane_crafting_chamber_l4", "arcane_crafting_chamber_l6", "arcane_crafting_chamber_l9"}) {
            helper.assertTrue(recipes.byKey(thaumicenergistics.ThaumicEnergistics.id("neoecoae/" + id)).isPresent(),
                    "Recipe " + id + " loads with ECO");
        }
    }
}
