package thaumicenergistics.common.grid;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
public class GridEssentiaCache {
    private final Map<ResourceLocation, Long> data = Collections.synchronizedMap(new LinkedHashMap<>());
    public long get(ResourceLocation id) { return data.getOrDefault(id, 0L); }
    public void put(ResourceLocation id, long amt) { data.put(id, amt); }
    public Map<ResourceLocation, Long> snapshot() { synchronized(data) { return new LinkedHashMap<>(data); } }
}
