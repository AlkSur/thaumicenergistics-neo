package thaumicenergistics.common.items;

import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.TCFunctionalItems;
import thaumcraft.common.vis.TCVisHelper;

import java.util.List;

public class ItemFocusAEWrench extends TCFunctionalItems.FocusItem {

    private static final AspectList CAST_COST = new AspectList().add(Aspect.FIRE, 10).add(Aspect.AIR, 10);

    public ItemFocusAEWrench() {
        super(TCFunctionalItems.FocusEffect.FIRE, new Properties().stacksTo(1));
    }

    @Override
    public int focusColor(ItemStack stack, float animationTicks) {
        return Aspect.ENERGY.getColor();
    }

    @Override
    public TCFunctionalItems.FocusCastingAnimation castingAnimation(ItemStack stack) {
        return TCFunctionalItems.FocusCastingAnimation.WAVE;
    }

    @Override
    public boolean isVisCostPerTick(ItemStack stack) {
        return false;
    }

    @Override
    public AspectList visCost(ItemStack stack) {
        return CAST_COST;
    }

    @Override
    public boolean requiresContinuousUse(ItemStack stack) {
        return false;
    }

    @Override
    public void clearContinuousState(ItemStack wandStack) {
    }

    @Override
    public boolean castContinuousFromWand(Level level, Player player, ItemStack wandStack,
                                          TCFunctionalItems.WandCastingItem wand, int remainingUseDuration) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        Item item = other.getItem();
        if (item instanceof TCFunctionalItems.WandCastingItem wand) {
            if (!level.isClientSide && wand.installFocus(other, stack, player)) {
                level.playSound(null, player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.2f);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        AspectList cost = this.visCost(stack);
        MutableComponent costLine = Component.translatable(
                        this.isVisCostPerTick(stack) ? "item.Focus.cost2" : "item.Focus.cost1")
                .append(":").withStyle(ChatFormatting.GRAY);
        for (Aspect aspect : cost.aspects()) {
            costLine = costLine.append(Component.literal(
                            " " + TCVisHelper.formatCentivis(cost.amount(aspect)))
                    .withStyle(style -> style.withColor(aspect.color())));
        }
        tooltip.add(costLine);
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean castFromWand(Level level, Player player, ItemStack wandStack,
                                TCFunctionalItems.WandCastingItem wand) {
        ItemStack focusStack = wand.getFocus(wandStack);
        if (focusStack.getItem() != this) {
            return false;
        }

        BlockHitResult hit = rayTrace(player, level, 24.0);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        if (!player.isShiftKeyDown()) {
            return false;
        }

        AspectList cost = this.visCost(focusStack);
        if (!wand.consumeVisCost(wandStack, player, cost, false)) {
            player.displayClientMessage(
                    Component.translatable("message.thaumcraft.wand.not_enough_vis"), true);
            return false;
        }
        if (!wand.consumeVisCost(wandStack, player, cost, true)) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        boolean handled = handleWrenchAction(level, player, hit, wandStack);

        if (handled) {
            spawnBeamParticles(level, player, pos);
            return true;
        }

        return false;
    }

    private boolean handleWrenchAction(Level level, Player player, BlockHitResult hit, ItemStack wandStack) {
        BlockPos pos = hit.getBlockPos();
        var blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof AEBaseBlockEntity aeBlockEntity) {
            InteractionResult result = aeBlockEntity.disassembleWithWrench(player, level, hit, wandStack);
            return result.consumesAction();
        }

        return false;
    }

    public static void spawnBeamParticles(Level level, Player player, BlockPos targetPos) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = Vec3.atCenterOf(targetPos);
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance < 0.5) {
            return;
        }
        Vec3 step = direction.normalize().scale(0.5);

        Vec3 current = start.add(step);
        int steps = (int) (distance / 0.5);
        for (int i = 0; i < steps; i++) {
            serverLevel.sendParticles(serverPlayer, ParticleTypes.END_ROD, true,
                    current.x, current.y, current.z,
                    1, 0.0, 0.0, 0.0, 0.0);
            current = current.add(step);
        }
    }

    private static BlockHitResult rayTrace(Player player, Level level, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }
}