package thaumicenergistics.api.grid;

import net.minecraft.resources.ResourceLocation;

/** 合成请求宿主 —— 接收合成请求。移植自 1.7.10。 */
public interface ICraftingIssuerHost {
    void launchCrafting(ResourceLocation aspectId, long amount);
}
