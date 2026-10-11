package thaumicenergistics.common.integration.appeng;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.essentia.EssentiaTransport;

/**
 * T-M2-001：ME 接口「源质对接逻辑」（{@code InterfaceLogic}）对外的桥接面。
 * <p>
 * <b>为什么需要这层接口</b>：TC 的寻址以<b>方块实体</b>为单
 * （{@code level.getBlockEntity(pos) instanceof EssentiaTransport}），
 * 而源质卡的网络 I/O（缓冲、记账、卡门控）实现在逻辑类 {@code InterfaceLogic} 上——二者在 Mixin 层无法
 * 互访私有成员，故以本接口作显式契约：{@code MixinInterfaceLogic} 实现之，
 * {@code MixinInterfaceBlockEntity} 经 {@code getInterfaceLogic()} 取得后调用。
 * <p>
 * <b>语义要点</b>：推入（外部 → 网络，进缓冲绝不丢弃）与拉取响应（<b>仅从缓存格 stock 槽取</b>，
 * 禁止直取网络）分列。广告的源质类型/数量一律取<b>缓存格</b>内容而非网络存量——这是与样板供应器
 * 的根本差异：接口不对外宣告网络源质，故不存在「管道无单抽走网络源质」的泄漏路径。
 * <p>
 * 方法一律以 {@code thE$} 前缀命名，避免污染混入目标的公共命名空间。
 */
public interface IEssentiaInterfaceLogic {

    /** 源质能力是否激活（源质卡在装 + 已联网）。未激活时全部对外表现为惰性。 */
    boolean thE$essentiaActive();

    /** 预检：网络当前还能接收多少源质（封顶于本卡速率）。须与 {@link #thE$acceptEssentia} 返回值严格一致。 */
    int thE$simulateAcceptEssentia(Aspect aspect, int amount);

    /** 接收源质（进缓冲 + 尽力注入网络，残留留缓冲绝不丢弃），返回接收量。 */
    int thE$acceptEssentia(Aspect aspect, int amount);

    /** 广告缓存格内首个非空源质要素（单要素契约）；无则 null。 */
    Aspect thE$stockAspect();

    /** 广告缓存格内指定要素的存量（跨槽累计）。 */
    int thE$stockAmount(Aspect aspect);

    /** 预检：缓存格还能取出多少。须与 {@link #thE$takeFromStock} 返回值严格一致。 */
    int thE$simulateTakeFromStock(Aspect aspect, int amount);

    /** 从缓存格取出源质（禁止直取网络），返回取出量。 */
    int thE$takeFromStock(Aspect aspect, int amount);
}
