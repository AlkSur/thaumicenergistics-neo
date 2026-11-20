package thaumicenergistics.tests;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.common.blockentities.AlchemicalFurnaceBlockEntity;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.items.ItemEssentiaCell;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModItems;

/** Checks for the F2 batch (machines and gameplay). */
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class F2Checks {
    private F2Checks() { }

    /** TET-07: one potentia no longer makes 63 cheap essentia burn as potentia. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vibrationChamberBurnsEachAspectAsItself(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get());
        TileEssentiaVibrationChamber chamber = helper.getBlockEntity(pos);
        helper.assertTrue(chamber.addEssentia(Aspect.AIR, 63, Direction.UP) == 63, "Aer fills");
        helper.assertTrue(chamber.addEssentia(Aspect.ENERGY, 5, Direction.UP) == 1, "Capacity is 64 in total");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(chamber.getStoredEssentia() == 63, "One unit burns at a time");
            helper.assertTrue(chamber.getCurrentAspect() == Aspect.ENERGY, "The potentia burns as potentia");
            var registries = helper.getLevel().registryAccess();
            var tag = chamber.saveWithFullMetadata(registries);
            chamber.loadWithComponents(tag, registries);
            helper.assertTrue(chamber.getStoredEssentia() == 63, "Per-aspect fuel survives reload");
            tag.remove("Fuel");
            tag.putInt("StoredEssentia", 70);
            tag.putString("AspectTag", "aer");
            chamber.loadWithComponents(tag, registries);
            helper.assertTrue(chamber.getStoredEssentia() == 64, "Old saves load, clamped to capacity");
            helper.succeed();
        });
    }

    /** TET-08: only ignis and potentia are pulled from the network. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void vibrationChamberPullsOnlyFuelFromNetwork(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get());
        TileEssentiaVibrationChamber chamber = helper.getBlockEntity(pos);
        var aqua = key(Aspect.WATER);
        var ignis = key(Aspect.FIRE);
        Map<AEKey, Long> stored = new HashMap<>(Map.of(aqua, 10L, ignis, 10L));
        MEStorage storage = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("fuel test"); }
            @Override public void getAvailableStacks(KeyCounter out) { stored.forEach(out::add); }
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                long taken = Math.min(amount, stored.getOrDefault(what, 0L));
                if (mode == Actionable.MODULATE) stored.merge(what, -taken, Long::sum);
                return taken;
            }
        };
        helper.runAfterDelay(20, () -> chamber.getGridNode(Direction.UP).getGrid().getStorageService()
                .addGlobalStorageProvider(mounts -> mounts.mount(storage, 0)));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(stored.get(ignis) < 10, "Ignis is pulled as fuel");
            helper.assertTrue(stored.get(aqua) == 10, "Aqua is never burned from the network");
            helper.succeed();
        });
    }

    /** TEG-04: budding crystals drop like AE2's budding quartz. */
    @GameTest(template = "empty")
    public static void buddingCrystalsDropLikeAe2(GameTestHelper helper) {
        var a = new BlockPos(1, 1, 1);
        var b = new BlockPos(3, 1, 1);
        helper.setBlock(a, ModBlocks.FLAWLESS_BUDDING_THAUMIC_CRYSTAL.get());
        helper.setBlock(b, ModBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL.get());
        breakWithPickaxe(helper, a);
        breakWithPickaxe(helper, b);
        helper.assertItemEntityPresent(ModItems.FLAWED_BUDDING_THAUMIC_CRYSTAL.get(), a, 1.0);
        helper.assertItemEntityPresent(ModItems.DAMAGED_BUDDING_THAUMIC_CRYSTAL.get(), b, 1.0);
        helper.succeed();
    }

    /** TEC-11: the creative cell supplies and voids its partitioned aspects. */
    @GameTest(template = "empty")
    public static void creativeCellIsInfinite(GameTestHelper helper) {
        var cell = new ItemStack(ModItems.ESSENTIA_CELL_CREATIVE.get());
        var aer = key(Aspect.AIR);
        helper.assertTrue(ItemEssentiaCell.addAspectToPartition(cell, aer.getId()), "Partition takes aer");
        var inventory = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(inventory != null, "AE2 must recognise the creative cell");
        var source = IActionSource.empty();
        helper.assertTrue(inventory.extract(aer, 1_000_000, Actionable.MODULATE, source) == 1_000_000, "Infinite supply");
        helper.assertTrue(inventory.insert(aer, 500, Actionable.MODULATE, source) == 500, "Partitioned essentia is voided");
        helper.assertTrue(inventory.extract(key(Aspect.FIRE), 5, Actionable.MODULATE, source) == 0,
                "Unpartitioned aspects are absent");
        helper.succeed();
    }

    /** TEC-01: ME access keeps the alchemical furnace's vis counter in step with its essentia. */
    @GameTest(template = "empty")
    public static void alchemicalFurnaceCounterFollowsMe(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "alchemical_furnace")));
        AlchemicalFurnaceBlockEntity furnace = helper.getBlockEntity(pos);
        int inserted = TCReflection.insertEssentia(furnace, new AspectStack(key(Aspect.AIR).getId(), 80));
        helper.assertTrue(inserted == AlchemicalFurnaceBlockEntity.MAX_VIS, "Insertion stops at the furnace limit");
        helper.assertTrue(furnace.pendingVis() == AlchemicalFurnaceBlockEntity.MAX_VIS, "Counter follows insertion");
        while (TCReflection.extractEssentia(furnace, 64) != null) { }
        helper.assertTrue(furnace.pendingVis() == 0, "A drained furnace must be free to smelt again");
        helper.succeed();
    }

    /** TEM-05: an ME interface part on a cable is a TC essentia endpoint. */
    @GameTest(template = "empty")
    public static void interfacePartIsAnEssentiaEndpoint(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        PartHelper.setPart(helper.getLevel(), pos, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(helper.getLevel(), pos, Direction.UP, null, AEParts.INTERFACE.get());
        helper.assertTrue(EssentiaCapabilities.find(helper.getLevel(), pos, Direction.UP) != null,
                "The interface part's face must expose an essentia transport");
        helper.succeed();
    }

    private static void breakWithPickaxe(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        var abs = helper.absolutePos(pos);
        net.minecraft.world.level.block.Block.dropResources(level.getBlockState(abs), level, abs, null, null,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
    }

    private static AEssentiaKey key(Aspect aspect) {
        return AEssentiaKey.of(AspectIds.getAspectId(aspect));
    }
}
