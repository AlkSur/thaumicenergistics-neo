package thaumicenergistics.common.grid;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.api.storage.IAspectStack;

public class EssentiaMonitor {
    private final Map<ResourceLocation, Long> storage = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    public List<IAspectStack> list() { synchronized(storage) { return storage.entrySet().stream().map(e -> (IAspectStack)new AspectStack(e.getKey(), e.getValue())).toList(); } }
    public long get(ResourceLocation id) { synchronized(storage) { return storage.getOrDefault(id, 0L); } }
    public void addListener(Runnable r) { listeners.add(r); }
    public void set(ResourceLocation id, long amt) { synchronized(storage) { storage.put(id, amt); } listeners.forEach(Runnable::run); }
}
