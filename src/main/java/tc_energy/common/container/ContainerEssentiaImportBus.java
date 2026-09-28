package tc_energy.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.definitions.AEItems;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.KeyTypeSelectionMenu;
import appeng.menu.interfaces.KeyTypeSelectionMenu.SyncedKeyTypes;
import appeng.api.util.KeyTypeSelection;
import tc_energy.common.parts.EssentiaImportBusPart;

public class ContainerEssentiaImportBus extends UpgradeableMenu<EssentiaImportBusPart>
        implements KeyTypeSelectionMenu {

    @appeng.menu.guisync.GuiSync(20)
    public SyncedKeyTypes importKeyTypes = new SyncedKeyTypes();

    public ContainerEssentiaImportBus(MenuType<?> menuType, int id, Inventory ip, EssentiaImportBusPart host) {
        super(menuType, id, ip, host);
    }

    @Override
    protected void setupConfig() {
        addExpandableConfigSlots(getHost().getConfig(), 2, 9, 5);
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        final int upgrades = getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD);
        return upgrades > idx;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide()) {
            if (getHost() instanceof KeyTypeSelectionHost selectionHost) {
                var enabled = selectionHost.getKeyTypeSelection().enabled();
                if (!importKeyTypes.keyTypes().equals(enabled)) {
                    importKeyTypes = new SyncedKeyTypes(enabled);
                }
            }
        }
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) getHost()).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return importKeyTypes;
    }
}
