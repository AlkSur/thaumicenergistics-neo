package thaumicenergistics.tests;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.init.ModItems;

/**
 * F1 micro-benchmark of the essentia queries machines make every tick, on a network holding many item
 * types. Opt-in: runs only with -Dthaumicenergistics.bench=true, otherwise it passes at once.
 */
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class F1Bench {
    private static final int ITEM_TYPES = 20_000;
    private static final int CALLS = 2_000;

    private F1Bench() { }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void essentiaQueriesOnALargeNetwork(GameTestHelper helper) {
        if (!Boolean.getBoolean("thaumicenergistics.bench")) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var cablePos = new BlockPos(1, 1, 1);
        helper.setBlock(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var abs = helper.absolutePos(cablePos);
        PartHelper.setPart(level, abs, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        EssentiaLevelEmitterPart emitter = PartHelper.setPart(level, abs, Direction.UP, null,
                ModItems.ESSENTIA_LEVEL_EMITTER.get());

        List<AEKey> items = new ArrayList<>(ITEM_TYPES);
        for (int i = 0; i < ITEM_TYPES; i++) {
            var stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("bench " + i));
            items.add(AEItemKey.of(stack));
        }
        var aer = AEssentiaKey.of(AspectIds.getAspectId(Aspect.AIR));
        var ignis = AEssentiaKey.of(AspectIds.getAspectId(Aspect.FIRE));
        MEStorage big = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("bench storage"); }
            @Override public void getAvailableStacks(KeyCounter out) {
                for (var key : items) out.add(key, 64);
                out.add(aer, 1000);
                out.add(ignis, 500);
            }
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                return 0;
            }
        };
        helper.runAfterDelay(40, () -> {
            var grid = emitter.getMainNode().getGrid();
            helper.assertTrue(grid != null, "Bench network must form");
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(big, 0));
        });
        helper.runAfterDelay(60, () -> {
            var grid = emitter.getMainNode().getGrid();
            var essentia = grid.getService(IEssentiaGrid.class);
            helper.assertTrue(essentia.getEssentiaAmount(aer.getId()) == 1000, "Bench storage must be visible");

            long t0 = System.nanoTime();
            long sink = 0;
            for (int i = 0; i < CALLS; i++) sink += essentia.getEssentiaAmount(aer.getId());
            long amountNs = (System.nanoTime() - t0) / CALLS;

            t0 = System.nanoTime();
            for (int i = 0; i < CALLS; i++) sink += essentia.getEssentiaList().size();
            long listNs = (System.nanoTime() - t0) / CALLS;

            t0 = System.nanoTime();
            for (int i = 0; i < CALLS; i++) emitter.setReportingValue(i + 1);
            long emitterNs = (System.nanoTime() - t0) / CALLS;

            thaumicenergistics.ThaumicEnergistics.LOG.info(
                    "[TE-BENCH] {} item types: getEssentiaAmount {} us, getEssentiaList {} us, "
                            + "emitter refresh {} us (sink {})",
                    ITEM_TYPES, amountNs / 1000.0, listNs / 1000.0, emitterNs / 1000.0, sink);
            helper.succeed();
        });
    }
}
