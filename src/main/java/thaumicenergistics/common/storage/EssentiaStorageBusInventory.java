package thaumicenergistics.common.storage;

import java.util.function.Supplier;
import java.util.Objects;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.storagebus.StorageBusPart;
import appeng.util.prioritylist.IPartitionList;

/** 新增视图也经过总线的访问和过滤规则；设置即时读取，避免与原句柄分叉。 */
public final class EssentiaStorageBusInventory extends MEInventoryHandler {
    private final Supplier<StorageBusPart> host;
    private AEKey[] cachedFilter;
    private FuzzyMode cachedFuzzyMode;

    public EssentiaStorageBusInventory(MEStorage storage, Supplier<StorageBusPart> host) {
        super(storage);
        this.host = host;
    }

    private void configure() {
        var bus = host.get();
        var settings = bus.getConfigManager();
        var access = settings.getSetting(Settings.ACCESS);
        setAllowInsertion(access.isAllowInsertion());
        setAllowExtraction(access.isAllowExtraction());
        setWhitelist(bus.getUpgrades().isInstalled(AEItems.INVERTER_CARD)
                ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        int usable = 18 + bus.getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9;
        var config = bus.getConfig();
        int size = Math.min(usable, config.size());
        FuzzyMode fuzzy = bus.getUpgrades().isInstalled(AEItems.FUZZY_CARD)
                ? settings.getSetting(Settings.FUZZY_MODE) : null;
        boolean changed = cachedFilter == null || cachedFilter.length != size || cachedFuzzyMode != fuzzy;
        if (!changed) {
            for (int i = 0; i < size; i++) {
                if (!Objects.equals(cachedFilter[i], config.getKey(i))) {
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            var builder = IPartitionList.builder();
            if (fuzzy != null) builder.fuzzyMode(fuzzy);
            cachedFilter = new AEKey[size];
            cachedFuzzyMode = fuzzy;
            for (int i = 0; i < size; i++) {
                cachedFilter[i] = config.getKey(i);
                builder.add(cachedFilter[i]);
            }
            setPartitionList(builder.build());
        }
        boolean filter = settings.getSetting(Settings.FILTER_ON_EXTRACT) == YesNo.YES;
        setExtractFiltering(filter, filter
                && settings.getSetting(Settings.STORAGE_FILTER) == StorageFilter.EXTRACTABLE_ONLY);
        setVoidOverflow(bus.getUpgrades().isInstalled(AEItems.VOID_CARD));
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        configure();
        return super.insert(key, amount, mode, source);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        configure();
        if (!host.get().getConfigManager().getSetting(Settings.ACCESS).isAllowExtraction()) {
            return 0;
        }
        return super.extract(key, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter stacks) {
        configure();
        super.getAvailableStacks(stacks);
    }
}
