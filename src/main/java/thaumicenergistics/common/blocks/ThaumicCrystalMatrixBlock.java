package thaumicenergistics.common.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import thaumicenergistics.init.ModBlocks;

/** Crystal growth adapted from BeiWuCN's Thaumic Energistics: CE (MIT). */
public final class ThaumicCrystalMatrixBlock extends AmethystBlock {
    public static final MapCodec<ThaumicCrystalMatrixBlock> CODEC = simpleCodec(ThaumicCrystalMatrixBlock::new);
    private static final Direction[] DIRECTIONS = Direction.values();

    public ThaumicCrystalMatrixBlock(Properties properties) { super(properties); }

    @Override public MapCodec<ThaumicCrystalMatrixBlock> codec() { return CODEC; }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int quality = quality();
        int chance = switch (quality) { case 0 -> 2; case 1 -> 3; case 2 -> 5; default -> 8; };
        if (random.nextInt(chance) != 0) return;
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos targetPos = pos.relative(direction);
        if (!level.hasChunkAt(targetPos)) return;
        BlockState target = level.getBlockState(targetPos);
        Block next = null;
        if (BuddingAmethystBlock.canClusterGrowAtState(target)) {
            next = ModBlocks.SMALL_MYSTIC_CRYSTAL_BUD.get();
        } else if (target.hasProperty(AmethystClusterBlock.FACING)
                && target.getValue(AmethystClusterBlock.FACING) == direction) {
            if (target.is(ModBlocks.SMALL_MYSTIC_CRYSTAL_BUD.get())) next = ModBlocks.MEDIUM_MYSTIC_CRYSTAL_BUD.get();
            else if (target.is(ModBlocks.MEDIUM_MYSTIC_CRYSTAL_BUD.get())) next = ModBlocks.LARGE_MYSTIC_CRYSTAL_BUD.get();
            else if (target.is(ModBlocks.LARGE_MYSTIC_CRYSTAL_BUD.get())) next = ModBlocks.MYSTIC_CRYSTAL_CLUSTER.get();
        }
        if (next == null) return;
        boolean changed = level.setBlockAndUpdate(targetPos, next.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, target.getFluidState().is(Fluids.WATER)));
        if (changed && quality != 0 && random.nextInt(12) == 0) {
            Block degraded = switch (quality) {
                case 1 -> ModBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL.get();
                case 2 -> ModBlocks.DAMAGED_BUDDING_THAUMIC_CRYSTAL.get();
                default -> ModBlocks.MYSTIC_CRYSTAL_BLOCK.get();
            };
            level.setBlockAndUpdate(pos, degraded.defaultBlockState());
        }
    }

    private int quality() {
        if (this == ModBlocks.FLAWLESS_BUDDING_THAUMIC_CRYSTAL.get()) return 0;
        if (this == ModBlocks.FLAWED_BUDDING_THAUMIC_CRYSTAL.get()) return 1;
        if (this == ModBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL.get()) return 2;
        return 3;
    }
}
