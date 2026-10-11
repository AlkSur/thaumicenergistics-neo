package thaumicenergistics.common.essentia.capability;

import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.mixins.ae2.bus.MixinIOBusPart;

/**
 * 卡路径（AE2 原版导入总线插卡）源质「抽取缓冲」的承载接口（鸭子接口，T-M1-007-⑦a 使用）。
 * <p>
 * 抽取顺序沿用 T-M1 批次对齐的原 ThE 导入总线（T-M2-REMOVE 已删）：非破坏性窥视 → 模拟入网测容量 →
 * 定向抽取 → 实插。当「模拟能收」与「实插实际收到」不一致（例如网络里挂载的容器存储无法如实模拟）时，
 * 已从容器抽出但未入网的源质必须暂存重试，<b>绝不丢弃</b>。
 * <p>
 * 该状态由 {@code MixinIOBusPart} 注入到 AE2 原版
 * {@code IOBusPart}（导入/导出总线共用父类）上实现，随宿主自身 NBT 持久化。使用<b>独立 NBT 键</b>
 * （见 {@link #NBT_KEY_ASPECT}），不与宿主原有 NBT 键冲突。
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
