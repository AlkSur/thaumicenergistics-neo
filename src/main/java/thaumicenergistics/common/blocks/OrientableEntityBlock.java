package thaumicenergistics.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumicenergistics.common.tiles.ThETileBase;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;

public abstract class OrientableEntityBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    protected OrientableEntityBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEssentiaVibrationChamber chamber)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // 检查手持物品是否为源质安瓿瓶
        if (!(stack.getItem() instanceof IEssentiaContainerItem container)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        AspectList aspects = container.getAspects(stack);
        if (aspects == null || aspects.size() == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        Aspect aspect = aspects.getAspects()[0];
        if (aspect == Aspect.FIRE || aspect == Aspect.ENERGY) {
            chamber.setSuctionType(aspect);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
