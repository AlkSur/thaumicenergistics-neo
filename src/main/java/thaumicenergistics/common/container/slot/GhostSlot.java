package thaumicenergistics.common.container.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 幽灵物品槽，用于知识记录仪的合成网格。
 * 槽内只是配方模板的幽灵副本（不消耗玩家物品）：所有点击由菜单的 clicked 拦截处理，
 * 因此绝不能被原版点击路径取出——mayPickup 必须为 false，否则双击（PICKUP_ALL）
 * 会把九格幽灵副本收进手持堆叠，凭空复制物品。
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
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
