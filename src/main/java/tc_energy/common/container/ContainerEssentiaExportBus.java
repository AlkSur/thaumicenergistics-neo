package tc_energy.common.container;

import appeng.api.config.Settings;
import appeng.api.stacks.AEKey;
import appeng.api.util.IConfigManager;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.KeyTypeSelectionMenu;
import appeng.menu.interfaces.KeyTypeSelectionMenu.SyncedKeyTypes;
import appeng.menu.slot.FakeSlot;
import tc_energy.common.parts.EssentiaExportBusPart;
import tc_energy.init.ModMenuTypes;

public class ContainerEssentiaExportBus extends UpgradeableMenu<EssentiaExportBusPart>
        implements KeyTypeSelectionMenu {

    @GuiSync(50)
    public long storedEnergy;

    @GuiSync(51)
    public SyncedKeyTypes exportKeyTypes = new SyncedKeyTypes();

    public ContainerEssentiaExportBus(int id, net.minecraft.world.entity.player.Inventory ip, EssentiaExportBusPart host) {
        super(ModMenuTypes.ESSENTIA_EXPORT_BUS.get(), id, ip, host);
        registerClientAction("clear", this::clear);
    }

    @Override
    protected void setupConfig() {
        var inv = getHost().getConfig().createMenuWrapper();
        for (int i = 0; i < 18; i++) {
            this.addSlot(new FakeSlot(inv, i), SlotSemantics.CONFIG);
        }
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return true;
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        if (cm.hasSetting(Settings.REDSTONE_CONTROLLED)) {
            this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_CONTROLLED));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isServerSide()) return;
        this.storedEnergy = getHost().getStoredEnergy();
        if (getHost() instanceof KeyTypeSelectionHost selectionHost) {
            var enabled = selectionHost.getKeyTypeSelection().enabled();
            if (!exportKeyTypes.keyTypes().equals(enabled)) {
                exportKeyTypes = new SyncedKeyTypes(enabled);
            }
        }
    }

    public void clear() {
        if (isClientSide()) {
            sendClientAction("clear");
        } else {
            getHost().getConfig().clear();
        }
    }

    public AEKey getConfiguredKey(int slot) {
        return getHost().getConfig().getKey(slot);
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) getHost()).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return exportKeyTypes;
    }
}
