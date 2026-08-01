package thaumicenergistics.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import thaumicenergistics.common.tiles.TileEssentiaProvider;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;
import thaumicenergistics.common.tiles.TileInfusionProvider;
import thaumicenergistics.common.tiles.ThETileBase;

public abstract class ThEBaseEntityBlock extends Block implements EntityBlock {
    protected ThEBaseEntityBlock(Properties p) { super(p); }

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == thaumicenergistics.init.ModBlockEntities.ESSENTIA_PROVIDER.get()) {
            return createTicker((lvl, pos, st, be) -> ((TileEssentiaProvider) be).serverTick());
        }
        if (type == thaumicenergistics.init.ModBlockEntities.INFUSION_PROVIDER.get()) {
            return createTicker((lvl, pos, st, be) -> ((TileInfusionProvider) be).serverTick());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityTicker<T> createTicker(BlockEntityTicker<? super T> ticker) {
        return (BlockEntityTicker<T>) ticker;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ThETileBase tb) tb.onBreakBlock();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
