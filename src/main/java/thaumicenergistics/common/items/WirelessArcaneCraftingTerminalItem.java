package thaumicenergistics.common.items;

import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.BlockHitResult;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;


/**
 * 无线奥术合成终端物品：绑定/能量/安全复用 AE2 无线终端基类，
 * 菜单复用有线奥术合成终端 {@code ContainerArcaneCraftingTerminal}（vis 记账单套）。
 */
public class WirelessArcaneCraftingTerminalItem extends WirelessTerminalItem {

    public WirelessArcaneCraftingTerminalItem(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Override
    public MenuType<?> getMenuType() {
        return thaumicenergistics.init.ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE;
    }

    @Override
    @Nullable
    public WirelessTerminalMenuHost<?> getMenuHost(
            Player player,
            ItemMenuHostLocator locator,
            @Nullable BlockHitResult hitResult) {
        return new WirelessArcaneCraftingTerminalMenuHost(
                this,
                player,
                locator,
                (p, subMenu) -> this.openFromInventory(p, locator, true));
    }
}
