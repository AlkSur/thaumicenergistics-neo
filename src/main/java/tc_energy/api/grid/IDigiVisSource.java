package tc_energy.api.grid;

/** 数字 vis 源——从 AE 网络提供 vis。移植自 1.7.10。 */
public interface IDigiVisSource {
    int consumeVis(net.minecraft.resources.ResourceLocation aspectId, int amount);
    boolean isActive();
}
