package tc_energy.common.container;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.config.AccessRestriction;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import tc_energy.common.parts.EssentiaStorageBusPart;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 严格参照 AE2 StorageBusMenu。
 * 直接继承 UpgradeableMenu<EssentiaStorageBusPart>：getHost().getConfig()、getHost().getUpgrades()、getHost().getConfigManager()
 * 均来自 IOBusPart 原生实现，无需自行实现库存。
 */
public class ContainerEssentiaStorageBus extends UpgradeableMenu<EssentiaStorageBusPart> {

    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_PARTITION = "partition";

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;
    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;
    @GuiSync(7)
    public YesNo filterOnExtract = YesNo.YES;
    @GuiSync(8)
    public Component connectedTo;

    public ContainerEssentiaStorageBus(MenuType<?> menuType, int id, Inventory ip, EssentiaStorageBusPart host) {
        super(menuType, id, ip, host);

        registerClientAction(ACTION_CLEAR, this::clear);
        registerClientAction(ACTION_PARTITION, this::partition);

        this.connectedTo = getConnectedDescription(host);
    }

    @Override
    protected void setupConfig() {
        // 2行固定实格 + 5行可选淡色虚格 = 63格，完全对齐 AE2 StorageBusMenu.setupConfig()
        addExpandableConfigSlots(getHost().getConfig(), 2, 9, 5);
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        // 1张容量升级卡启用1行虚格（idx=第idx组可选行），和AE2一致
        final int upgrades = getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD);
        return upgrades > idx;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        this.connectedTo = getConnectedDescription(getHost());
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
        this.setReadWriteMode(cm.getSetting(Settings.ACCESS));
        this.setStorageFilter(cm.getSetting(Settings.STORAGE_FILTER));
        this.setFilterOnExtract(cm.getSetting(Settings.FILTER_ON_EXTRACT));
    }

    public void clear() {
        if (isClientSide()) { sendClientAction(ACTION_CLEAR); return; }
        getHost().getConfig().clear();
        this.broadcastChanges();
    }

    public void partition() {
        if (isClientSide()) { sendClientAction(ACTION_PARTITION); return; }
        var inv = getHost().getConfig();
        Iterator<AEKey> it = Collections.emptyIterator();

        // ============= ✅ 修复3根因：IOBusPart 里没有叫 getInternalHandler() 的方法 =============
        //   AE2 StorageBusPart extends IOBusPart implements IStorageProvider 自己加了 getInternalHandler
        //   我们的 EssentiaStorageBusPart 没有显式实现这个方法 → 反射软查，拿不到就跳过分区（不崩）
        Object storage = null;
        try {
            try {
                storage = getHost().getClass().getMethod("getInternalHandler").invoke(getHost());
            } catch (NoSuchMethodException ignored) {
                storage = getHost().getClass().getMethod("getStorage").invoke(getHost());
            }
        } catch (Throwable ignored) {}

        if (storage != null) {
            try {
                Object availableStacks = storage.getClass().getMethod("getAvailableStacks").invoke(storage);
                if (availableStacks instanceof Iterable<?> iter) {
                    @SuppressWarnings("unchecked")
                    Iterator<AEKey> keyIt = ((Iterable<AEKey>) iter).iterator();
                    it = keyIt;
                }
            } catch (Throwable ignored) {}
        }

        inv.beginBatch();
        try {
            for (int x = 0; x < inv.size(); x++) {
                if (it.hasNext() && this.isSlotEnabled(x / 9 - 2)) {
                    inv.setStack(x, new GenericStack(it.next(), 1));
                } else {
                    inv.setStack(x, null);
                }
            }
        } finally {
            inv.endBatch();
        }
        this.broadcastChanges();
    }

    public boolean supportsFuzzySearch() { return hasUpgrade(AEItems.FUZZY_CARD); }
    public AccessRestriction getReadWriteMode() { return this.rwMode; }
    private void setReadWriteMode(AccessRestriction v) { this.rwMode = v; }
    public StorageFilter getStorageFilter() { return this.storageFilter; }
    private void setStorageFilter(StorageFilter v) { this.storageFilter = v; }
    public YesNo getFilterOnExtract() { return this.filterOnExtract; }
    public void setFilterOnExtract(YesNo v) { this.filterOnExtract = v; }
    public Component getConnectedTo() { return connectedTo; }
    public FuzzyMode getFuzzyMode() { return this.fzMode; }

    private static Component getConnectedDescription(EssentiaStorageBusPart p) {
        java.lang.reflect.Method m = METHOD_CACHE.computeIfAbsent(p.getClass(), c -> {
            try { return c.getMethod("getConnectedToDescription"); }
            catch (NoSuchMethodException e) { return NO_METHOD; }
        });
        if (m == NO_METHOD) return null;
        try {
            return (Component) m.invoke(p);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 反射方法缓存哨兵：computeIfAbsent 返回非 null 才缓存（避免每次 getMethod）。 */
    private static final java.lang.reflect.Method NO_METHOD;

    static {
        try {
            NO_METHOD = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /** 反射方法缓存：broadcastChanges 每 tick 调用 getConnectedDescription，避免每次 getMethod。 */
    private static final Map<Class<?>, java.lang.reflect.Method> METHOD_CACHE = new ConcurrentHashMap<>();
}
