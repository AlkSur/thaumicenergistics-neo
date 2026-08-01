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

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        BlockState rotated = state.rotate(level, pos, Rotation.CLOCKWISE_90);

        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
            ItemFocusAEWrench.spawnBeamParticles(level, player, pos);
            event.setCanceled(true);
        }
    }
}