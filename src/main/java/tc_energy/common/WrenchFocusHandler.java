package tc_energy.common;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.items.ItemFocusAEWrench;
import tc_energy.init.ModItems;

/**
 * AE 扳手焦点行为：shift+左键旋转 AE 方块（Ren 版无 addon focus 挂点，改为直接检测本物品）。
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class WrenchFocusHandler {

    private WrenchFocusHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldStack = player.getMainHandItem();

        if (!(heldStack.getItem() instanceof ItemFocusAEWrench)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        if (ItemFocusAEWrench.rotateBlock(player.level(), player, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
