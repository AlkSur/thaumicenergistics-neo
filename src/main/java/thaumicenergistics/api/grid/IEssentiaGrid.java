package thaumicenergistics.api.grid;

import net.minecraft.resources.ResourceLocation;
import appeng.api.networking.IGridService;
import thaumicenergistics.api.storage.IAspectStack;

import java.util.List;

/**
 * AE2 Grid 网络的源质网格服务。
 *
 * 通过 {@code grid.getCache(IEssentiaGrid.class)} 获取。
 * 提供源质的存取查询接口，供无线傀儡背包、源质提供器等使用。
 *
 * 注意：这是方案A（轻量级）实现——源质作为独立的 GridService，
 * 不注册为 AE2 的 AEKeyType。后续可升级为方案B（原生 AEKeyType 集成）。
 */
public interface IEssentiaGrid extends IGridService {

    /**
     * 获取网络中指定源质的存储量。
     *
     * @param aspectId 源质 ID（如 "thaumcraft:ignis"）
     * @return 存储量，不存在返回 0
     */
    long getEssentiaAmount(ResourceLocation aspectId);

    /**
     * 从网络提取源质。
     *
     * @param aspectId 源质 ID
     * @param amount 要提取的量
     * @param simulate true 为模拟，false 为实际执行
     * @return 实际提取的量
     */
    long extractEssentia(ResourceLocation aspectId, long amount, boolean simulate);

    /**
     * 向网络注入源质。
     *
     * @param aspectId 源质 ID
     * @param amount 要注入的量
     * @param simulate true 为模拟，false 为实际执行
     * @return 无法注入的量（0 表示全部成功）
     */
    long injectEssentia(ResourceLocation aspectId, long amount, boolean simulate);

    List<IAspectStack> getEssentiaList();
}
