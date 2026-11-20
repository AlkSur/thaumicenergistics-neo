package thaumicenergistics.tests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.common.registry.TCBlockEntityTypes;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModItems;

/** Checks for phase 2 (usability and integrations). */
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class P2Checks {
    private P2Checks() { }

    /** TEL-04: the wireless arcane terminal can be worn in a Curios slot like AE2's terminals. */
    @GameTest(template = "empty")
    public static void wirelessArcaneTerminalIsACurio(GameTestHelper helper) {
        var curio = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "curio"));
        helper.assertTrue(new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()).is(curio),
                "Terminal must be in curios:curio");
        helper.succeed();
    }

    /** TEU-01: an assembler waiting for vis reports which primal and how much. */
    @GameTest(template = "empty")
    public static void assemblerReportsMissingVis(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos, thaumicenergistics.init.ModBlocks.ARCANE_ASSEMBLER.get());
        thaumicenergistics.common.tiles.TileArcaneAssembler tile = helper.getBlockEntity(pos);
        var registries = helper.getLevel().registryAccess();
        var saved = tile.saveWithFullMetadata(registries);
        saved.putBoolean("IsCrafting", true);
        saved.put("CurrentCraftResult", new ItemStack(net.minecraft.world.item.Items.DIAMOND).save(registries));
        var aspects = new net.minecraft.nbt.CompoundTag();
        new thaumcraft.api.aspects.AspectList().add(thaumcraft.api.aspects.Aspect.ORDER, 5).writeToNBT(aspects);
        saved.put("CurrentCraftAspects", aspects);
        tile.loadWithComponents(saved, registries);
        var wait = tile.visWait();
        helper.assertTrue(wait != null && wait.aspect() == thaumcraft.api.aspects.Aspect.ORDER,
                "The order waits for ordo");
        helper.assertTrue(wait.haveCvis() == 0 && wait.needCvis() > 0, "and says how much is missing");
        helper.succeed();
    }

    /** TEU-05: a wired arcane terminal without a vis frequency reports it. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void arcaneTerminalReportsVisSource(GameTestHelper helper) {
        var cablePos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(cablePos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        var abs = helper.absolutePos(cablePos);
        appeng.api.parts.PartHelper.setPart(helper.getLevel(), abs, null, null,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        thaumicenergistics.common.parts.ArcaneCraftingTerminalPart part = appeng.api.parts.PartHelper.setPart(
                helper.getLevel(), abs, net.minecraft.core.Direction.UP, null, ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(part.visSourceState() == thaumicenergistics.common.parts.ArcaneCraftingTerminalPart.VIS_SOURCE_NONE,
                    "No vis interface on the network means no vis source");
            helper.succeed();
        });
    }

    /** TEL-03: TE's recipes and research come from data packs, with the phase-2 additions. */
    @GameTest(template = "empty")
    public static void recipesAndResearchAreData(GameTestHelper helper) {
        var outputs = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (Object recipe : thaumcraft.api.ThaumcraftApi.getCraftingRecipes()) {
            if (recipe instanceof thaumcraft.api.crafting.IArcaneRecipe arcane) outputs.add(arcane.getRecipeOutput().getItem());
            if (recipe instanceof thaumcraft.api.crafting.InfusionRecipe infusion) outputs.add(infusion.getRecipeOutput().getItem());
        }
        helper.assertTrue(outputs.contains(ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get()), "Terminal recipe loaded");
        helper.assertTrue(outputs.contains(thaumicenergistics.init.ModBlocks.ARCANE_ASSEMBLER.get().asItem()), "Assembler infusion loaded");
        helper.assertTrue(outputs.contains(ModItems.PROBABILITY_ACCELERATOR_CARD.get()), "Accelerator card has a recipe (TEU-10)");
        var entries = thaumcraft.common.research.TCResearchIndex.entries();
        var terminal = entries.stream().filter(e -> e.key().equals("ARCANECRAFTINGTERMINAL")).findFirst();
        helper.assertTrue(terminal.isPresent(), "Research loaded from data");
        helper.assertTrue(terminal.get().parents().contains("MYSTICPROCESSOR"), "Processor is a prerequisite (TEU-03)");
        helper.assertTrue(entries.stream().filter(e -> e.key().startsWith("PSEUDO_")).count() == 10, "Ten pseudo prerequisites");
        helper.succeed();
    }

    /** TEU-04 / decision 6: an assembler with no vis source and no stored vis refuses an order. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void assemblerWithoutVisRefusesOrders(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, thaumicenergistics.init.ModBlocks.ARCANE_ASSEMBLER.get());
        helper.runAfterDelay(40, () -> {
            thaumicenergistics.common.tiles.TileArcaneAssembler tile = helper.getBlockEntity(pos);
            helper.assertFalse(tile.isVisSourceAvailable(), "No vis interface on the network");
            ItemStack[] grid = new ItemStack[9];
            java.util.Arrays.fill(grid, ItemStack.EMPTY);
            grid[0] = new ItemStack(net.minecraft.world.item.Items.STONE);
            var pattern = thaumicenergistics.common.tiles.TileArcaneAssembler.buildArcanePattern(
                    new thaumicenergistics.common.integration.tc.ArcaneCraftingPattern(
                            new thaumcraft.api.aspects.AspectList().add(thaumcraft.api.aspects.Aspect.AIR, 2),
                            new ItemStack(net.minecraft.world.item.Items.DIAMOND), grid));
            var holder = new appeng.api.stacks.KeyCounter();
            holder.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE), 1);
            helper.assertFalse(tile.pushPattern(pattern, new appeng.api.stacks.KeyCounter[]{holder},
                    net.minecraft.core.Direction.UP), "Order refused instead of waiting forever");
            helper.assertFalse(tile.isCrafting(), "Nothing started");
            helper.succeed();
        });
    }

    /** TEU-06: the server config is registered and loaded with its defaults. */
    @GameTest(template = "empty")
    public static void serverConfigIsLoaded(GameTestHelper helper) {
        helper.assertTrue(thaumicenergistics.config.ThEConfig.SPEC.isLoaded(), "Server config loaded");
        helper.assertTrue(thaumicenergistics.config.ThEConfig.providerMaxReceivers() == 8, "Default receivers");
        helper.assertTrue(thaumicenergistics.config.ThEConfig.assemblerVisCache() == 187, "Default vis cache");
        var cell = new ItemStack(ModItems.ESSENTIA_CELL_1K.get());
        helper.assertTrue(((thaumicenergistics.common.items.ItemEssentiaCell) cell.getItem()).getBytes(cell) == 1024,
                "1k cell keeps 1024 bytes at multiplier 1");
        helper.succeed();
    }

    /** TEL-07: an addon's endpoint provider answers for its own block entities before TE's generic code. */
    @GameTest(template = "empty")
    public static void addonEndpointProviderIsConsulted(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "jar_of_essentia")));
        var jar = helper.getBlockEntity(pos);
        var aer = thaumicenergistics.common.integration.tc.AspectIds.getAspectId(thaumcraft.api.aspects.Aspect.AIR);
        int[] inserted = {0};
        thaumicenergistics.api.essentia.EssentiaEndpointProviders.register(
                new thaumicenergistics.api.essentia.IEssentiaEndpointProvider() {
                    @Override public boolean owns(net.minecraft.world.level.block.entity.BlockEntity be) { return be == jar; }
                    @Override public java.util.List<thaumicenergistics.api.storage.AspectStack> contents(
                            net.minecraft.world.level.block.entity.BlockEntity be, net.minecraft.core.Direction side) {
                        return java.util.List.of(new thaumicenergistics.api.storage.AspectStack(aer, 42));
                    }
                    @Override public thaumicenergistics.api.storage.AspectStack extract(
                            net.minecraft.world.level.block.entity.BlockEntity be, ResourceLocation id, int amount,
                            net.minecraft.core.Direction side) {
                        return new thaumicenergistics.api.storage.AspectStack(id, Math.min(amount, 7));
                    }
                    @Override public int insert(net.minecraft.world.level.block.entity.BlockEntity be, ResourceLocation id,
                            int amount, net.minecraft.core.Direction side) {
                        inserted[0] += amount;
                        return amount;
                    }
                });
        var endpoints = thaumicenergistics.common.integration.tc.EssentiaEndpoints.class;
        helper.assertTrue(thaumicenergistics.common.integration.tc.EssentiaEndpoints.contents(jar, null).getFirst().amount() == 42,
                "Contents come from the provider");
        helper.assertTrue(thaumicenergistics.common.integration.tc.EssentiaEndpoints.extract(jar, aer, 10, null).amount() == 7,
                "Extraction goes through the provider");
        helper.assertTrue(thaumicenergistics.common.integration.tc.EssentiaEndpoints.insert(jar, aer, 5, null) == 5
                && inserted[0] == 5, "Insertion goes through the provider");
        helper.succeed();
    }

    /** E2: the assembler takes an exact batch of N crafts (as a Neo ECOAE CPU pushes) and pays N times the vis. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void assemblerTakesParallelBatches(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, thaumicenergistics.init.ModBlocks.ARCANE_ASSEMBLER.get());
        helper.runAfterDelay(40, () -> {
            thaumicenergistics.common.tiles.TileArcaneAssembler tile = helper.getBlockEntity(pos);
            var registries = helper.getLevel().registryAccess();
            var saved = tile.saveWithFullMetadata(registries);
            var stored = new net.minecraft.nbt.CompoundTag();
            stored.putInt(thaumcraft.api.aspects.Aspect.AIR.getTag(), 1000);
            saved.put("StoredVis", stored);
            tile.loadWithComponents(saved, registries);
            tile.setSpeedUpgrades(4);
            helper.assertTrue(tile.parallelSlots() >= 3, "Four speed cards allow a batch of at least 3");
            ItemStack[] grid = new ItemStack[9];
            java.util.Arrays.fill(grid, ItemStack.EMPTY);
            grid[0] = new ItemStack(net.minecraft.world.item.Items.STONE);
            var pattern = thaumicenergistics.common.tiles.TileArcaneAssembler.buildArcanePattern(
                    new thaumicenergistics.common.integration.tc.ArcaneCraftingPattern(
                            new thaumcraft.api.aspects.AspectList().add(thaumcraft.api.aspects.Aspect.AIR, 2),
                            new ItemStack(net.minecraft.world.item.Items.DIAMOND), grid));
            var stone = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE);
            var uneven = new appeng.api.stacks.KeyCounter();
            uneven.add(stone, 4);
            helper.assertFalse(tile.pushBatch(pattern, new appeng.api.stacks.KeyCounter[]{uneven}, 3),
                    "4 stone cannot be 3 crafts of 1");
            helper.assertTrue(uneven.get(stone) == 4, "A refused batch leaves its inputs untouched");
            var total = new appeng.api.stacks.KeyCounter();
            total.add(stone, 3);
            helper.assertTrue(tile.pushBatch(pattern, new appeng.api.stacks.KeyCounter[]{total}, 3), "Batch of 3 taken");
            helper.assertTrue(total.get(stone) == 0, "The batch owns all its inputs");
            int perCraft = Math.round(2 * 10 * 0.8F);
            helper.assertTrue(tile.requiredCvisForJob(thaumcraft.api.aspects.Aspect.AIR) == perCraft * 3,
                    "Vis is paid for every craft in the batch");
            helper.assertTrue(tile.parallelSlots() == 0, "A busy assembler offers no slots");
            helper.succeed();
        });
    }

    /** E2/E6/TEN-02: with Neo ECOAE installed (-PcompatNeoEco=true), its APIs see the assembler and big orders. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void neoEcoIntegration(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("neoecoae")) {
            helper.succeed();
            return;
        }
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, thaumicenergistics.init.ModBlocks.ARCANE_ASSEMBLER.get());
        helper.runAfterDelay(40, () -> {
            NeoEcoChecks.run(helper, helper.getBlockEntity(pos));
            helper.succeed();
        });
    }

    /** E3: Neo ECO AE essentia storage matrices (only with -PcompatNeoEco=true). */
    @GameTest(template = "empty")
    public static void neoEcoEssentiaMatrix(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("neoecoae")) {
            helper.assertTrue(!thaumicenergistics.common.integration.neoecoae.NeoEcoItems.enabled(), "No matrices without ECO");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                    thaumicenergistics.ThaumicEnergistics.id("neoecoae/eco_essentia_cell_housing")).isEmpty(),
                    "Matrix recipes are skipped without ECO");
            helper.succeed();
            return;
        }
        NeoEcoChecks.storage(helper);
        helper.succeed();
    }

    /** E8: the arcane crafting chamber (only with -PcompatNeoEco=true). */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void neoEcoArcaneCraftingChamber(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("neoecoae")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                    thaumicenergistics.ThaumicEnergistics.id("neoecoae/arcane_crafting_chamber_l4")).isEmpty(),
                    "Chamber recipes are skipped without ECO");
            helper.succeed();
            return;
        }
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, thaumicenergistics.common.integration.neoecoae.NeoEcoItems.CHAMBER_L4.get());
        helper.runAfterDelay(40, () -> {
            NeoEcoChecks.chamber(helper, helper.getBlockEntity(pos));
            helper.succeed();
        });
    }

    /** TEL-02: block entities that cannot be drained are listed in a tag packs can extend. */
    @GameTest(template = "empty")
    public static void undrainableContainersAreTagged(GameTestHelper helper) {
        var pedestal = BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(TCBlockEntityTypes.WAND_RECHARGE_PEDESTAL.get());
        helper.assertTrue(pedestal.is(TCReflection.ESSENTIA_STORE_BLACKLIST),
                "The wand recharge pedestal is blacklisted through the tag");
        helper.succeed();
    }
}
