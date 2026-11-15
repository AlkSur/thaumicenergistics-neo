package thaumicenergistics.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumcraft.common.items.TCFunctionalItems;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.items.ItemFocusAEWrench;
import thaumicenergistics.init.ModItems;

@EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class WrenchFocusHandler {

    private WrenchFocusHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        ItemStack heldStack = player.getMainHandItem();

        if (!(heldStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) {
            return;
        }

        ItemStack focusStack = wand.getFocus(heldStack);
        if (focusStack.isEmpty() || focusStack.getItem() != ModItems.FOCUS_AEWRENCH.get()) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        if (level.isClientSide()) {
            return;
        }
        // 服务端对 START / STOP / ABORT 都会触发 LeftClickBlock：只在 START 处理，
        // 否则一次点击旋转两次（180°）。
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }

        BlockPos pos = event.getPos();
        // 只旋转 ME 设备。对任意方块调用 rotate 会把床、门、双箱等多格方块的单格转歪，
        // 下一次形状更新整件消失且无掉落；同时遵守冒险模式与出生点保护。
        if (!ItemFocusAEWrench.isRotationTarget(level, pos)
                || !thaumicenergistics.common.blocks.ThEWrenchHandler.mayModify(player, level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState rotated = state.rotate(level, pos, Rotation.CLOCKWISE_90);

        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
            ItemFocusAEWrench.spawnBeamParticles(level, player, pos);
            event.setCanceled(true);
        }
    }
}