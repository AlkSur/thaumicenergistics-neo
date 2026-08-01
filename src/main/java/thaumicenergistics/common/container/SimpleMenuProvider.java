package thaumicenergistics.common.container;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.MenuProvider;

import java.util.function.BiFunction;

/** 不依赖AE2 SubMenu 体系的 MenuProvider，给三个总线打开自定义GUI用 */
public record SimpleMenuProvider<T extends AbstractContainerMenu>(
        Component title,
        MenuType<T> type,
        BiFunction<Integer, Inventory, T> factory
) implements MenuProvider {

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return factory.apply(id, playerInv);
    }
}