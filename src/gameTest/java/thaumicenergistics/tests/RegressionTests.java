package thaumicenergistics.tests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumicenergistics.common.container.EssentiaFillHelper;
import thaumicenergistics.common.integration.tc.ArcaneCraftingPattern;
import thaumicenergistics.common.integration.tc.EssentiaItemContainers;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.tiles.TileArcaneAssembler;
import thaumicenergistics.common.integration.appeng.EssentiaContainerItemStrategy;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

/** 只在隔离验收运行中加载，不进入发布 JAR。 */
@Mod("thaumicenergistics_tests")
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class RegressionTests {
    @GameTest(template = "empty")
    public static void jarsSupportEveryBusFace(GameTestHelper helper) {
        EssentiaBusChecks.jarsFromEveryFace(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exportBusSuppliesFertilityLamp(GameTestHelper helper) {
        EssentiaBusChecks.fertilityLampPullsFromExportBus(helper);
    }

    public RegressionTests(net.neoforged.bus.api.IEventBus bus) {
        WirelessChargingChecks.register(bus);
    }

    @GameTest(template = "empty")
    public static void partialDepositConservesEssentia(GameTestHelper helper) {
        var original = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "glass_phial")));
        var filled = EssentiaItemContainers.withContents(original, new AspectList().add(Aspect.AIR, 8));
        helper.assertTrue(filled != null, "Generic phial must retain arbitrary essentia amounts");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        long[] stored = {0};
        MEStorage storage = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("partial test storage"); }
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                long accepted = Math.min(amount, 3 - stored[0]);
                if (mode == Actionable.MODULATE) stored[0] += accepted;
                return accepted;
            }
        };
        var remainder = EssentiaFillHelper.depositContainerContent(storage,
                (amount, mode, multiplier) -> amount, IActionSource.ofPlayer(player), player, filled);
        helper.assertTrue(stored[0] == 3, "Only actual acceptance reaches the network");
        helper.assertTrue(EssentiaItemContainers.contents(remainder).getAmount(Aspect.AIR) == 5,
                "Five unaccepted essentia must remain in the physical phial");
        helper.assertTrue(EssentiaItemContainers.contents(filled).getAmount(Aspect.AIR) == 8,
                "Building a replacement must not mutate the source component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recoveredDropsRemainUsable(GameTestHelper helper) {
        var key = AEssentiaKey.of(TCReflection.getAspectId(Aspect.AIR));
        var drops = new java.util.ArrayList<ItemStack>();
        key.addDrops(123, drops, helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        helper.assertTrue(drops.size() == 1, "Essentia needs a recoverable physical drop");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, drops.getFirst());
        var strategy = new EssentiaContainerItemStrategy();
        var context = strategy.findPlayerSlotContext(player, 0);
        helper.assertTrue(context != null, "Recovery drops must have an extraction context");
        helper.assertTrue(strategy.extract(context, key, 23, Actionable.SIMULATE) == 23, "Simulation quantity");
        helper.assertTrue(strategy.getExtractableContent(context).amount() == 123, "Simulation cannot consume drops");
        helper.assertTrue(strategy.extract(context, key, 23, Actionable.MODULATE) == 23, "Actual recovery quantity");
        helper.assertTrue(strategy.getExtractableContent(context).amount() == 100, "Unrecovered resources survive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patternIdentityAndActualInputs(GameTestHelper helper) {
        ItemStack[] first = new ItemStack[9];
        java.util.Arrays.fill(first, ItemStack.EMPTY);
        first[0] = new ItemStack(Items.STONE);
        var aspects = new AspectList().add(Aspect.AIR, 1);
        var pattern = TileArcaneAssembler.buildArcanePattern(
                new ArcaneCraftingPattern(aspects, new ItemStack(Items.DIAMOND), first));
        ItemStack[] second = first.clone();
        second[0] = new ItemStack(Items.DIRT);
        var other = TileArcaneAssembler.buildArcanePattern(
                new ArcaneCraftingPattern(aspects, new ItemStack(Items.DIAMOND), second));
        helper.assertFalse(pattern.equals(other), "Same output with different inputs is a different pattern");
        var holder = new KeyCounter();
        holder.add(appeng.api.stacks.AEItemKey.of(Items.STONE), 1);
        helper.assertTrue(pattern.materializeInputs(new KeyCounter[]{holder}, helper.getLevel()) != null,
                "Real matching input should be accepted");
        helper.assertTrue(holder.get(appeng.api.stacks.AEItemKey.of(Items.STONE)) == 1,
                "Preflight must not consume CPU inputs");
        holder.add(appeng.api.stacks.AEItemKey.of(Items.DIRT), 1);
        helper.assertTrue(pattern.materializeInputs(new KeyCounter[]{holder}, helper.getLevel()) == null,
                "Extra or mismatched materials must not be silently consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pendingCraftSurvivesReload(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos, thaumicenergistics.init.ModBlocks.ARCANE_ASSEMBLER.get());
        TileArcaneAssembler tile = helper.getBlockEntity(pos);
        var registries = helper.getLevel().registryAccess();
        var saved = tile.saveWithFullMetadata(registries);
        saved.putBoolean("IsCrafting", true);
        saved.putInt("CraftTick", 20);
        saved.putBoolean("ResultProduced", true);
        saved.put("CurrentCraftResult", new ItemStack(Items.DIAMOND, 4).save(registries));
        var outputs = new net.minecraft.nbt.ListTag();
        outputs.add(new ItemStack(Items.DIAMOND, 2).save(registries));
        saved.put("PendingCraftOutputs", outputs);
        var aspects = new net.minecraft.nbt.CompoundTag();
        new AspectList().add(Aspect.AIR, 3).writeToNBT(aspects);
        saved.put("CurrentCraftAspects", aspects);
        var stored = new net.minecraft.nbt.CompoundTag();
        stored.putInt(Aspect.AIR.getTag(), 17);
        saved.put("StoredVis", stored);
        tile.loadWithComponents(saved, registries);
        var restored = tile.saveWithFullMetadata(registries);
        helper.assertTrue(tile.isCrafting(), "Pending output must remain busy across reload");
        helper.assertTrue(restored.getBoolean("ResultProduced"), "Completion phase must survive reload");
        helper.assertTrue(ItemStack.parseOptional(registries,
                restored.getList("PendingCraftOutputs", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)).getCount() == 2,
                "Only the unaccepted part of output may be retried");
        helper.assertTrue(tile.getStoredVis().getAmount(Aspect.AIR) == 17, "Reload must not charge vis again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void encodedWispAspectSurvivesPlanning(GameTestHelper helper) {
        ItemStack essence = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "wisp_essence")));
        helper.assertTrue(essence.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem,
                "Generic wisp essence must expose its aspect component");
        ((thaumcraft.api.aspects.IEssentiaContainerItem) essence.getItem()).setAspects(
                essence, new AspectList().add(Aspect.AIR, 2));
        helper.assertFalse(essence.getComponentsPatch().isEmpty(), "Aspect must be encoded on the item");
        ItemStack[] grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = essence;
        var encoded = new ArcaneCraftingPattern(new AspectList().add(Aspect.AIR, 1),
                new ItemStack(Items.DIAMOND), grid);
        var restored = new ArcaneCraftingPattern(encoded.writeToNBT(new net.minecraft.nbt.CompoundTag()));
        var input = TileArcaneAssembler.buildArcanePattern(restored).getInputs()[0];
        var expected = appeng.api.stacks.AEItemKey.of(essence);
        helper.assertTrue(input.getPossibleInputs().length == 1,
                "Explicit aspect components must not be replaced by bare tag variants");
        helper.assertTrue(input.getPossibleInputs()[0].what().equals(expected),
                "The crafting plan must show the encoded aspect after serialization");
        helper.assertFalse(input.isValid(appeng.api.stacks.AEItemKey.of(essence.getItem()), helper.getLevel()),
                "Unknown-aspect essence must not satisfy the encoded aspect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectedWispVariantIsPrimary(GameTestHelper helper) {
        ItemStack essence = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "wisp_essence_ordo")));
        helper.assertFalse(essence.isEmpty(), "Selected wisp variant must exist");
        ItemStack[] grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = essence;
        var pattern = TileArcaneAssembler.buildArcanePattern(new ArcaneCraftingPattern(
                new AspectList().add(Aspect.AIR, 1), new ItemStack(Items.DIAMOND), grid));
        var inputs = pattern.getInputs()[0].getPossibleInputs();
        helper.assertTrue(inputs[0].what().equals(appeng.api.stacks.AEItemKey.of(essence)),
                "The selected variant, not the first tag member, must be the primary input");
        var unknown = appeng.api.stacks.AEItemKey.of(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "wisp_essence")));
        for (var input : inputs) {
            helper.assertFalse(input.what().equals(unknown), "Bare unknown-aspect essence is not a substitute");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalWirelessMerge(GameTestHelper helper) {
        if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")) WirelessMergeChecks.run(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void closedWirelessTerminalChargesWand(GameTestHelper helper) {
        WirelessChargingChecks.run(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyWirelessTerminalsSurvivePlayerTick(GameTestHelper helper) {
        WirelessChargingChecks.emptyTerminals(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void probabilityBrainUsesContinuousPower(GameTestHelper helper) {
        helper.setBlock(net.minecraft.core.BlockPos.ZERO, thaumicenergistics.init.ModBlocks.BRAIN_OF_PROBABILITY.get());
        helper.runAfterDelay(2, () -> {
            var brain = (thaumicenergistics.common.tiles.BrainOfProbabilityBlockEntity)
                    helper.getBlockEntity(net.minecraft.core.BlockPos.ZERO);
            var node = brain.getActionableNode();
            helper.assertTrue(node != null && node.getIdlePowerUsage() == 50.0,
                    "The real grid node must advertise 50 AE per tick");
            helper.succeed();
        });
    }
}
