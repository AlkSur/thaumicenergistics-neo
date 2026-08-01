package thaumicenergistics.api.grid;

import net.minecraft.resources.ResourceLocation;

/** 源质存量变化时通知宿主。移植自 1.7.10。 */
public interface IEssentiaWatcherHost {
    void onEssentiaChange(ResourceLocation aspectId, long storedAmount, long changeAmount);
    void updateWatcher(IEssentiaWatcher newWatcher);
}
