package thaumicenergistics.common.inventory;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

public final class PersistedTerminalInventories {
    private PersistedTerminalInventories() {
    }

    public static AppEngInternalInventory createPersistedInv(Player player, ItemStack stack, int size,
            DataComponentType<ItemContainerContents> component) {
        var inv = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory changed) {
                stack.set(component, changed.toItemContainerContents());
            }

            @Override
            public boolean isClientSide() {
                return player.level().isClientSide();
            }
        }, size);
        inv.fromItemContainerContents(stack.getOrDefault(component, ItemContainerContents.EMPTY));
        return inv;
    }
}
