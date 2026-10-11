package thaumicenergistics.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.StringRepresentable;

import thaumicenergistics.common.tiles.BrainOfProbabilityBlockEntity;
import thaumcraft.common.registry.TCItems;
import thaumicenergistics.init.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import thaumicenergistics.common.blocks.ThEBaseEntityBlock;
import thaumicenergistics.init.ModBlocks;

/**
 * 概率之脑下格（M6，CE gacha_box 同构）：BE 所在格。
 * 方块状态 = facing（屏幕朝向）× screen（五态屏）× jar（是否装脑）；
 * 交互无 GUI：装脑 / 插加速卡 / 潜行取出，见 B3 批次。
 */
public class BrainOfProbabilityBlock extends ThEBaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 五态屏：off=无脑 / on=有脑待机 / working=周期运转 / failed=白转后闪 / success=出货后闪。 */
    public static final EnumProperty<Screen> SCREEN = EnumProperty.create("screen", Screen.class);
    /** 镜像槽里的罐装脑（thaumcraft:brain_jar）。 */
    public static final BooleanProperty JAR = BooleanProperty.create("jar");

    public BrainOfProbabilityBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SCREEN, Screen.OFF)
                .setValue(JAR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCREEN, JAR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 放置成功后补齐上格（上格 STACKED=true，见 BrainOfProbabilityTopBlock）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), ModBlocks.BRAIN_OF_PROBABILITY_TOP.get()
                .defaultBlockState()
                .setValue(BrainOfProbabilityTopBlock.STACKED, true), 3);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrainOfProbabilityBlockEntity(pos, state);
    }

    /** 五态屏枚举（CE Screen 同名同序）。 */
    public enum Screen implements StringRepresentable {
        OFF("off"),
        ON("on"),
        WORKING("working"),
        FAILED("failed"),
        SUCCESS("success");

        private final String name;

        Screen(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    // ===== 交互（M6 语义：无 GUI 纯右键，CE BlockGachaBox.useItemOn 同构） =====

    /** 手持物品右键：潜行→取出全部；脑→装入并记归属；加速卡→直插（上限 4）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, state, player, stack);
    }

    /** 空手右键：潜行→取出全部；普通→actionbar 状态反馈。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                   Player player, BlockHitResult hit) {
        ItemInteractionResult result = interact(level, pos, state, player, ItemStack.EMPTY);
        if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * 统一交互入口；上格点击时由 Top 方块下借一格调用。
     * 顺序照 CE：潜行取出 → 无主脑认领 → 装脑 → 插卡。
     */
    static ItemInteractionResult interact(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof BrainOfProbabilityBlockEntity brain)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 潜行取回：不管手上拿着什么，空手也得能操作
        if (player.isShiftKeyDown()) {
            if (!state.getValue(JAR)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            if (!brain.mayTakeBrain(player)) {
                player.displayClientMessage(Component.translatable(
                        "thaumicenergistics.brain_prob.bound_to_other",
                        brain.ownerName() == null ? "" : brain.ownerName()), true);
                return ItemInteractionResult.SUCCESS;
            }
            // 先解绑：不管取出什么，下一颗脑归新主人
            brain.unbind();
            give(player, brain.takeBrain());
            for (ItemStack card : brain.takeCards()) {
                give(player, card);
            }
            player.displayClientMessage(
                    Component.translatable("thaumicenergistics.brain_prob.ejected"), true);
            return ItemInteractionResult.SUCCESS;
        }
        // 槽里残留的无主脑（存档中断留下）：点击的玩家一键认领
        if (brain.hasUnboundBrain()) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            brain.bind(player);
            player.displayClientMessage(Component.translatable(
                    "thaumicenergistics.brain_prob.rebound", player.getName().getString()), true);
            return ItemInteractionResult.SUCCESS;
        }
        // 装脑：已装则拒收；空手跳过（T-M6-RECIPE-2：投入物改 thaumcraft 罐装脑）
        if (!stack.isEmpty() && !state.getValue(JAR) && stack.is(TCItems.BRAIN_JAR.get())) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            brain.addBrain(player, stack);
            player.displayClientMessage(Component.translatable(
                    "thaumicenergistics.brain_prob.brain_inserted", player.getName().getString()), true);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        // 插加速卡：已满提示且不消耗
        if (!stack.isEmpty() && stack.is(ModItems.PROBABILITY_ACCELERATOR_CARD.get())) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            if (!brain.hasRoomForCard()) {
                player.displayClientMessage(
                        Component.translatable("thaumicenergistics.brain_prob.cards_full"), true);
                return ItemInteractionResult.SUCCESS;
            }
            brain.addCard(stack);
            player.displayClientMessage(Component.translatable(
                    "thaumicenergistics.brain_prob.card_inserted", brain.cardCount()), true);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        // 空手普通右键：状态反馈
        if (stack.isEmpty()) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            player.displayClientMessage(brain.buildStatus(), true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 物品给玩家（背包满则落地），CE give 同款。 */
    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
