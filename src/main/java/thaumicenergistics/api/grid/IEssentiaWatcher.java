package thaumicenergistics.api.grid;

import java.util.Collection;
import net.minecraft.resources.ResourceLocation;

/** 监听源质网格变化并通知宿主。移植自 1.7.10。 */
public interface IEssentiaWatcher extends Collection<ResourceLocation> {
    IEssentiaWatcherHost getHost();
}
