package tc_energy.common.grid;

import appeng.api.networking.IGrid;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 管理 AE2 Grid → EssentiaMonitor 的映射。
 * 每个 AE2 网格对应一个独立的 EssentiaMonitor 实例，
 * 确保不同网络的源质存储互不干扰。
 * 使用 WeakHashMap，当 Grid 被 GC 时自动清理。
 */
public final class EssentiaMonitorRegistry {

    private EssentiaMonitorRegistry() {}

    private static final Map<IGrid, EssentiaMonitor> MONITORS = new WeakHashMap<>();

    /**
     * 获取指定 Grid 对应的 EssentiaMonitor。
     * 如果不存在则创建一个新的。
     */
    public static EssentiaMonitor getMonitor(IGrid grid) {
        synchronized (MONITORS) {
            return MONITORS.computeIfAbsent(grid, g -> new EssentiaMonitor());
        }
    }
}
