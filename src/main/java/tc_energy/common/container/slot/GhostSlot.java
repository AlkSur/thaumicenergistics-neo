package tc_energy.common.container.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 幽灵物品槽，用于知识记录仪的合成网格。
 * 玩家可以放置任意物品，也可以取回。
 */
public class GhostSlot extends Slot {
    public GhostSlot(Container inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
