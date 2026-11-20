package thaumicenergistics.tests;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.common.tiles.InfusionRiskCalculator;
import thaumicenergistics.init.ModItems;

/** Behaviour checks for the F1 performance batch: the faster paths must report the same values. */
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class F1Checks {
    private F1Checks() { }

    /** TEC-02/TEM-04: cached lookups see essentia mixed with items, and the emitter reacts to it. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void cachedEssentiaQueriesMatchStorage(GameTestHelper helper) {
        var level = helper.getLevel();
        var cablePos = new BlockPos(1, 1, 1);
        helper.setBlock(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var abs = helper.absolutePos(cablePos);
        PartHelper.setPart(level, abs, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        EssentiaLevelEmitterPart emitter = PartHelper.setPart(level, abs, Direction.UP, null,
                ModItems.ESSENTIA_LEVEL_EMITTER.get());
        var aer = AEssentiaKey.of(AspectIds.getAspectId(Aspect.AIR));
        var ignis = AEssentiaKey.of(AspectIds.getAspectId(Aspect.FIRE));
        MEStorage storage = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("mixed storage"); }
            @Override public void getAvailableStacks(KeyCounter out) {
                out.add(AEItemKey.of(Items.STONE), 999);
                // A freshly decoded key, as a storage cell would produce: lookups must still match.
                out.add(new AEssentiaKey(ResourceLocation.parse(aer.getId().toString())), 1000);
                out.add(ignis, 500);
            }
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                return 0;
            }
        };
        helper.runAfterDelay(40, () -> {
            var grid = emitter.getMainNode().getGrid();
            helper.assertTrue(grid != null, "Network must form");
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(storage, 0));
        });
        helper.runAfterDelay(60, () -> {
            var grid = emitter.getMainNode().getGrid();
            var essentia = grid.getService(IEssentiaGrid.class);
            helper.assertTrue(essentia.getEssentiaAmount(aer.getId()) == 1000, "Aer amount");
            helper.assertTrue(essentia.getEssentiaAmount(ignis.getId()) == 500, "Ignis amount");
            var list = essentia.getEssentiaList();
            helper.assertTrue(list.size() == 2, "Only the two stored aspects are listed, got " + list);
            emitter.setReportingValue(1000);
            int atThousand = emitter.isProvidingWeakPower();
            emitter.setReportingValue(2000);
            int atTwoThousand = emitter.isProvidingWeakPower();
            helper.assertTrue(atThousand != atTwoThousand,
                    "All-essentia emitter must see the 1500 total between its two thresholds");
            helper.succeed();
        });
    }

    /** TET-11: the chunk walk finds a nearby infusion matrix. */
    @GameTest(template = "empty")
    public static void infusionScanFindsNearbyMatrix(GameTestHelper helper) {
        var matrixPos = new BlockPos(3, 2, 3);
        helper.setBlock(matrixPos, BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "infusion_matrix")));
        var info = InfusionRiskCalculator.scanNearbyMatrix(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(info != null && info.matrixPos().equals(helper.absolutePos(matrixPos)),
                "Matrix within range must be found");
        helper.succeed();
    }

    /** TEA2-10: a terminal with nothing to charge sleeps. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void idleArcaneTerminalSleeps(GameTestHelper helper) {
        var cablePos = new BlockPos(1, 1, 1);
        helper.setBlock(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var abs = helper.absolutePos(cablePos);
        PartHelper.setPart(helper.getLevel(), abs, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        ArcaneCraftingTerminalPart part = PartHelper.setPart(helper.getLevel(), abs, Direction.UP, null,
                ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get());
        helper.runAfterDelay(40, () -> {
            var node = part.getMainNode().getNode();
            helper.assertTrue(part.tickingRequest(node, 1) == TickRateModulation.SLEEP,
                    "No vis source and no wand: nothing to charge");
            helper.succeed();
        });
    }
}
