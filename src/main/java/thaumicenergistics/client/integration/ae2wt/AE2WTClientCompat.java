package thaumicenergistics.client.integration.ae2wt;

import appeng.client.gui.style.StyleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.init.ModMenuTypes;

/** 与普通客户端入口隔离，未安装 AE2WT 时不加载其屏幕 API。 */
public final class AE2WTClientCompat {
    private AE2WTClientCompat() {
    }

    public static void registerScreen(RegisterMenuScreensEvent event) {
        // 无线屏专用样式：继承有线布局，叠加官方三键——scrollingUpgrades/upgradeScrollbar
        // 摆位 + 普通 upgrades 面板挪出屏外（-9999），防滚动/普通双面板抢位。
        event.register(ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                (ContainerArcaneCraftingTerminal menu,
                        Inventory inventory,
                        Component title) -> new AE2WTArcaneTerminalScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal_wireless.json")));
    }
}
