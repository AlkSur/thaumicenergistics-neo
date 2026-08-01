package thaumicenergistics.common.items;

import java.util.function.BiConsumer;

import net.minecraft.world.entity.player.Player;
import appeng.api.util.KeyTypeSelection;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;

/**
 * 无线源质终端的 MenuHost：继承 AE2 WirelessTerminalMenuHost，覆盖 getKeyTypeSelection()
 * 仅允许源质类型（其余 WAP 连接/电力/存储复用父类）。
 */
public class WirelessEssentiaTerminalMenuHost
        extends WirelessTerminalMenuHost<WirelessEssentiaTerminalItem> {

    public WirelessEssentiaTerminalMenuHost(
            WirelessEssentiaTerminalItem item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    /**
     * 仅允许源质类型，禁止物品和流体（与 EssentiaTerminalPart 一致）。
     */
    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return new KeyTypeSelection(
                () -> {},
                keyType -> keyType == thaumicenergistics.common.integration.appeng.AEssentiaKeyType.INSTANCE);
    }
}
