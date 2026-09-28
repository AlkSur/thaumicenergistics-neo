package tc_energy.common.grid;

import net.minecraft.resources.ResourceLocation;
import tc_energy.api.grid.IEssentiaWatcher;
import tc_energy.api.grid.IEssentiaWatcherHost;
import java.util.*;

/**
 * 监听源质网格的变化并通知 host。
 */
public class EssentiaWatcher implements IEssentiaWatcher {
    private final IEssentiaWatcherHost host;
    private final Set<ResourceLocation> watchedAspects = new HashSet<>();
    private final Map<ResourceLocation, Long> lastAmounts = new HashMap<>();
    private final EssentiaMonitor monitor;

    public EssentiaWatcher(IEssentiaWatcherHost host, EssentiaMonitor monitor) {
        this.host = host;
        this.monitor = monitor;
    }

    @Override public IEssentiaWatcherHost getHost() { return host; }
    @Override public int size() { return watchedAspects.size(); }
    @Override public boolean isEmpty() { return watchedAspects.isEmpty(); }
    @Override public boolean contains(Object o) { return watchedAspects.contains(o); }
    @Override public Iterator<ResourceLocation> iterator() { return watchedAspects.iterator(); }
    @Override public Object[] toArray() { return watchedAspects.toArray(); }
    @Override public <T> T[] toArray(T[] a) { return watchedAspects.toArray(a); }
    @Override public boolean add(ResourceLocation id) { return watchedAspects.add(id); }
    @Override public boolean remove(Object o) { lastAmounts.remove(o); return watchedAspects.remove(o); }
    @Override public boolean containsAll(Collection<?> c) { return watchedAspects.containsAll(c); }
    @Override public boolean addAll(Collection<? extends ResourceLocation> c) { return watchedAspects.addAll(c); }
    @Override public boolean removeAll(Collection<?> c) { lastAmounts.keySet().removeAll(c); return watchedAspects.removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { lastAmounts.keySet().retainAll(c); return watchedAspects.retainAll(c); }
    @Override public void clear() { lastAmounts.clear(); watchedAspects.clear(); }

    /** 检查所有被监听的源质，并在变化时通知 host。 */
    public void tick() {
        for (ResourceLocation id : watchedAspects) {
            long current = monitor.get(id);
            Long last = lastAmounts.get(id);
            if (last == null || last != current) {
                lastAmounts.put(id, current);
                host.onEssentiaChange(id, current, 0);
            }
        }
    }
}
