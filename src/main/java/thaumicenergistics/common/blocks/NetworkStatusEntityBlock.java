package thaumicenergistics.common.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** An ME machine whose visual state follows the server's active grid node. */
public abstract class NetworkStatusEntityBlock extends ThEBaseEntityBlock {
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    protected NetworkStatusEntityBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ONLINE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ONLINE);
    }

    public static void updateOnlineState(BlockEntity tile, boolean online) {
        var level = tile.getLevel();
        if (level == null || level.isClientSide() || tile.isRemoved() || !level.hasChunkAt(tile.getBlockPos())) return;
        var state = tile.getBlockState();
        if (state.hasProperty(ONLINE) && state.getValue(ONLINE) != online) {
            level.setBlock(tile.getBlockPos(), state.setValue(ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }
}
