package thaumicenergistics.common.essentia.capability;

import thaumicenergistics.api.storage.AspectStack;

/**
 * 总线拥有的源质传输缓冲，由 IOBusPart 注入实现并持久化。
 * 模拟与实收不一致时保留未入网资源，等待后续重试。
 */
public interface EssentiaTransferBuffer {

    /** 宿主 NBT 键：缓冲源质要素 id（{@code StringTag}）。 */
    String NBT_KEY_ASPECT = "ThECardBufferAspect";

    /** 宿主 NBT 键：缓冲源质量（{@code LongTag}）；键缺失或 &le; 0 视为无缓冲。 */
    String NBT_KEY_AMOUNT = "ThECardBufferAmount";

    /** 当前抽取缓冲（无缓冲时返回 {@link AspectStack#EMPTY}）。 */
    AspectStack thE$getTransferBuffer();

    /** 覆盖式写入抽取缓冲（{@code null} 视为清空），并触发宿主落盘。 */
    void thE$setTransferBuffer(AspectStack buffer);
}
