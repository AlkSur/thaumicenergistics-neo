package thaumicenergistics.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 带玩家背包的基础容器。所有 ThE 容器都继承它。 */
public abstract class ThEContainerBase extends AbstractContainerMenu {
    protected final Inventory playerInv;
    protected final int playerInvStart;

    // ===== AE2 标准坐标（源自 storage_bus.json + player_inventory.json）=====
    // 所有 ThE 容器（无论是否走 UpgradeableMenu）都以此基准对齐。
    public static final int AE2_IMAGE_HEIGHT = 253;
    public static final int AE2_PLAYER_INV_Y = AE2_IMAGE_HEIGHT - 84;  // 169
    public static final int AE2_HOTBAR_Y = AE2_IMAGE_HEIGHT - 26;      // 227
    public static final int AE2_PLAYER_INV_X = 8;

    protected ThEContainerBase(MenuType<?> type, int id, Inventory playerInv, int customSlots) {
        super(type, id);
        this.playerInv = playerInv;
        this.playerInvStart = customSlots;
        addPlayerInventory(customSlots);
    }

    private void addPlayerInventory(int start) {
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++)
            addSlot(new Slot(playerInv, c + r * 9 + 9,
                    AE2_PLAYER_INV_X + c * 18, AE2_PLAYER_INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(playerInv, c,
                    AE2_PLAYER_INV_X + c * 18, AE2_HOTBAR_Y));
    }

    @Override public ItemStack quickMoveStack(Player p, int idx) {
        ItemStack r = ItemStack.EMPTY; Slot s = slots.get(idx);
        if (s.hasItem()) { ItemStack st = s.getItem(); r = st.copy();
            if (idx < playerInvStart) { if (!moveItemStackTo(st, playerInvStart, slots.size(), true)) return ItemStack.EMPTY; }
            else if (!moveItemStackTo(st, 0, playerInvStart, false)) return ItemStack.EMPTY;
            if (st.isEmpty()) s.set(ItemStack.EMPTY); else s.setChanged();
        } return r;
    }

    @Override public boolean stillValid(Player p) { return true; }
}
