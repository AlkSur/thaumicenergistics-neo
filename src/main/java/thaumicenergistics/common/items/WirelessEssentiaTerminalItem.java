package thaumicenergistics.common.items;

import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;

/**
 * 无线源质终端物品。继承 AE2 的WirelessTerminalItem
 * 右键使用时打开仅显示源质的 AE2 无线终端 GUI。
 */
public class WirelessEssentiaTerminalItem extends WirelessTerminalItem {

    public WirelessEssentiaTerminalItem(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Override
    public MenuType<?> getMenuType() {
        return thaumicenergistics.init.ModMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get();
    }

    @Override
    @Nullable
    public WirelessTerminalMenuHost<?> getMenuHost(
            Player player,
            ItemMenuHostLocator locator,
            @Nullable BlockHitResult hitResult) {
        return new WirelessEssentiaTerminalMenuHost(
                this,
                player,
                locator,
                (p, subMenu) -> this.openFromInventory(p, locator, true));
    }
}
