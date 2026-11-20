package thaumicenergistics.common.integration.neoecoae;

import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.tiles.TileArcaneAssembler;

/**
 * Parallel arcane assembly for Neo ECO AE Extension (E2): an ECO crafting CPU may push a batch of N identical
 * arcane crafts to one Arcane Assembler, which pays N times the vis. Loaded only when Neo ECOAE 21.2.10+ (the
 * version with ECOParallelCraftingProviders) is installed; TE classes never implement ECO interfaces.
 */
public final class ThENeoEcoParallel {
    private ThENeoEcoParallel() {
    }

    public static void register() {
        ECOParallelCraftingProviders.register(TileArcaneAssembler.class,
                provider -> provider instanceof TileArcaneAssembler assembler ? new Contract(assembler) : null);
    }

    private record Contract(TileArcaneAssembler assembler) implements ECOParallelCraftingProvider {
        @Override
        public int eco$getAvailableParallelSlots() {
            return assembler.parallelSlots();
        }

        @Override
        public boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputTotal, long craftCount,
                @Nullable UUID craftingJobId) {
            return assembler.pushBatch(pattern, inputTotal, craftCount);
        }
    }
}
