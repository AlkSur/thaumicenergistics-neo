package thaumicenergistics.api.grid;

/** 合成请求宿主 —— 接收合成请求。移植自 1.7.10。 */
public interface ICraftingIssuerHost {
    void launchCrafting(net.minecraft.resources.ResourceLocation aspectId, long amount);
}
