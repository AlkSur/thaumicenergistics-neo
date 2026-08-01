package thaumicenergistics.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.stacks.AEKey;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.FakeSlot;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;

/**
 * 源质发信器 Menu — 与 AE2 原版 StorageLevelEmitterMenu 逻辑一致。
 * 使用 reportingValue 代替 thresholdLevel，与 AbstractLevelEmitterPart 对齐。
 */
public class ContainerEssentiaLevelEmitter extends UpgradeableMenu<EssentiaLevelEmitterPart> {

    private static final String ACTION_SET_REPORTING_VALUE = "setReportingValue";

    @GuiSync(10)
    public long reportingValue;

    @GuiSync(11)
    public String trackedAspectId = "";

    @GuiSync(12)
    public long currentLevel;

    public ContainerEssentiaLevelEmitter(MenuType<?> menuType, int id, Inventory ip, EssentiaLevelEmitterPart host) {
        super(menuType, id, ip, host);
        registerClientAction(ACTION_SET_REPORTING_VALUE, Long.class, this::setReportingValue);
    }

    @Override
    protected void setupConfig() {
        var inv = getHost().getConfig().createMenuWrapper();
        this.addSlot(new FakeSlot(inv, 0), SlotSemantics.CONFIG);
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return true;
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        if (cm.hasSetting(Settings.REDSTONE_EMITTER)) {
            this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_EMITTER));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isServerSide()) return;

        EssentiaLevelEmitterPart part = getHost();

        long hostReporting = part.getReportingValue();
        if (this.reportingValue != hostReporting) {
            this.reportingValue = hostReporting;
        }

        long hostCurrent = part.getCurrentLevel();
        if (this.currentLevel != hostCurrent) {
            this.currentLevel = hostCurrent;
        }

        AEssentiaKey key = part.getConfiguredEssentiaKey();
        String aspectId = key != null ? key.getId().toString() : "";
        if (!this.trackedAspectId.equals(aspectId)) {
            this.trackedAspectId = aspectId;
        }

        // 红石模式由 IConfigManager 自动同步
    }

    public AEKey getConfiguredKey() {
        return getHost().getConfig().getKey(0);
    }

    public void setReportingValue(long value) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_REPORTING_VALUE, value);
        } else {
            getHost().setReportingValue(value);
        }
    }

    public Aspect getTrackedAspect() {
        if (trackedAspectId.isEmpty()) return null;
        return Aspect.getAspect(trackedAspectId);
    }
}
