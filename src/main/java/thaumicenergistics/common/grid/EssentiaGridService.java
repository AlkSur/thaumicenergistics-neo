package thaumicenergistics.common.grid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.networking.security.IActionSource;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.api.storage.IAspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

/**
 * 源质网格服务的实现。查询和提取源质时同时检查两个存储后端：
 * Path A: EssentiaMonitor（注魔供应器/傀儡背包直接写入），
 * Path B: AE2 原生存储（源质存储元件、输入总线等）。
 */
public class EssentiaGridService implements IEssentiaGrid, IGridServiceProvider {

    private final EssentiaMonitor monitor;
    private final IGrid grid;

    public EssentiaGridService(IGrid grid) {
        this.monitor = EssentiaMonitorRegistry.getMonitor(grid);
        this.grid = grid;
    }

    private long getNativeAmount(ResourceLocation aspectId) {
        var storage = getNativeStorage();
        if (storage == null) return 0;
        var key = AEssentiaKey.of(aspectId);
        if (key == null) return 0;
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEssentiaKey ek && ek.equals(key)) {
                return entry.getLongValue();
            }
        }
        return 0;
    }

    private long extractNative(ResourceLocation aspectId, long amount, boolean simulate) {
        var storage = getNativeStorage();
        if (storage == null) return 0;
        var key = AEssentiaKey.of(aspectId);
        if (key == null) return 0;
        return StorageHelper.poweredExtraction(
                grid.getEnergyService(), storage, key, amount,
                IActionSource.empty());
    }

    private MEStorage getNativeStorage() {
        var sg = grid.getService(IStorageService.class);
        return sg != null ? sg.getInventory() : null;
    }

    @Override
    public long getEssentiaAmount(ResourceLocation aspectId) {
        long monitorAmt = monitor.get(aspectId);
        long nativeAmt = getNativeAmount(aspectId);
        return monitorAmt + nativeAmt;
    }

    @Override
    public long extractEssentia(ResourceLocation aspectId, long amount, boolean simulate) {
        // 优先从 monitor 提取
        long monitorAmt = monitor.get(aspectId);
        if (monitorAmt >= amount) {
            if (!simulate) {
                monitor.set(aspectId, monitorAmt - amount);
            }
            return amount;
        }

        // monitor 不够，全部取出后再从 AE2 原生存储提取剩余
        long extracted = 0;
        if (monitorAmt > 0) {
            if (!simulate) {
                monitor.set(aspectId, 0);
            }
            extracted = monitorAmt;
        }

        long remaining = amount - extracted;
        if (remaining > 0) {
            long nativeExtracted = extractNative(aspectId, remaining, simulate);
            extracted += nativeExtracted;
        }

        return extracted;
    }

    @Override
    public long injectEssentia(ResourceLocation aspectId, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        if (!simulate) {
            long current = monitor.get(aspectId);
            monitor.set(aspectId, current + amount);
        }
        return 0;
    }

    @Override
    public List<IAspectStack> getEssentiaList() {
        Map<ResourceLocation, Long> merged = new LinkedHashMap<>();

        for (var stack : monitor.list()) {
            merged.put(stack.aspectId(), stack.amount());
        }

        var storage = getNativeStorage();
        if (storage != null) {
            for (var entry : storage.getAvailableStacks()) {
                if (entry.getKey() instanceof AEssentiaKey ek) {
                    merged.merge(ek.getId(), entry.getLongValue(), Long::sum);
                }
            }
        }

        List<IAspectStack> result = new ArrayList<>();
        for (var entry : merged.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(new AspectStack(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }
}