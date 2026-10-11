package thaumicenergistics.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 概率之脑上格（M6，CE aggregator 同构）：纯形态格，无 BE、无交互；
 * 状态只表示它是否立在底座（下格）上。
 */
public class BrainOfProbabilityTopBlock extends Block {

    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");

    public BrainOfProbabilityTopBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(STACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STACKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(STACKED, isOnBody(context.getLevel(), context.getClickedPos()));
    }

    /** 底座被拆时上格随之消散。 */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (facing == Direction.DOWN
                && !(level.getBlockState(pos.below()).getBlock() instanceof BrainOfProbabilityBlock)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** 上格破坏时掉落本格对应的上格物品（供恢复结构用）。 */
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return java.util.List.of();
    }

    private static boolean isOnBody(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof BrainOfProbabilityBlock;
    }

    /** 上格点击下借一格：全部交互交给底座处理。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                                                  Level level, BlockPos pos,
                                                                  Player player,
                                                                  InteractionHand hand,
                                                                  BlockHitResult hit) {
        BlockPos bodyPos = pos.below();
        BlockState bodyState = level.getBlockState(bodyPos);
        if (bodyState.getBlock() instanceof BrainOfProbabilityBlock) {
            return BrainOfProbabilityBlock.interact(level, bodyPos, bodyState, player, stack);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                                                   BlockPos pos, Player player,
                                                                   BlockHitResult hit) {
        BlockPos bodyPos = pos.below();
        BlockState bodyState = level.getBlockState(bodyPos);
        if (bodyState.getBlock() instanceof BrainOfProbabilityBlock) {
            ItemInteractionResult result =
                    BrainOfProbabilityBlock.interact(level, bodyPos, bodyState, player, ItemStack.EMPTY);
            if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return InteractionResult.PASS;
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    /** 上格不遮挡底座交互面（与 CE aggregator 一致：模型本身不占满格）。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // CE 模型实际占位约在角落四柱与中部，给近乎全格碰撞以避免玩家穿模（宽松近似）
        return Block.box(1, 0, 1, 15, 16, 15);
    }
}
