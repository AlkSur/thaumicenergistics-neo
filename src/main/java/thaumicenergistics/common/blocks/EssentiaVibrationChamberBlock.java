package thaumicenergistics.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;

/** Keeps the chamber's existing horizontal facing and adds a visual generating state. */
public final class EssentiaVibrationChamberBlock extends OrientableEntityBlock {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public EssentiaVibrationChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEssentiaVibrationChamber(pos, state);
    }
}
