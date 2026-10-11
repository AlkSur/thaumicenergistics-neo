package thaumicenergistics.mixins.ae2.interfaces;

import net.minecraft.core.Direction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.essentia.EssentiaTransport;

import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;

/**
 * T-M2-001：给 AE2 <b>原版 ME 接口（方块形态）</b>注入 TC 源质运输接口，使其成为可被 TC 管网寻址的
 * 源质端点（推入 + 拉取响应）。
 * <p>
 * <b>为什么实现的是 {@code thaumcraft.common.essentia.EssentiaTransport}（common 子接口）</b>：TC 261 中
 * 管道与机器一律以 {@code level.getBlockEntity(pos) instanceof
 * thaumcraft.common.essentia.EssentiaTransport} 判定目标，common 子接口 extends api 接口，实现它即可同时满足两者。
 * <p>
 * <b>为什么落在 BlockEntity</b>：TC 的寻址单位是方块实体——被动机器（推入 / 拉取响应）依赖 TC 侧主动调用
 * {@code addEssentia}/{@code takeEssentia}。AE2 部件形态挂在线缆总线上、世界方块实体是
 * {@code CableBusBlockEntity}，TC 无法命中 → 本任务按规格<b>仅覆盖方块形态</b>。
 * <p>
 * <b>与样板供应器的根本差异（防外泄）</b>：本类 {@code getSuctionAmount} 恒 0（零吸力，管道永不路由向接口），
 * 且 {@code getEssentiaType}/{@code getEssentiaAmount} 广告的是<b>缓存格 stock 槽</b>内容而非网络存量——
 * 故即便拉取响应开放，外部也只能拉走用户在缓存格里标记的那点源质，<b>不存在「无单抽走网络源质」的泄漏路径</b>
 * （对照 T-M1-018 供应器泄漏缺陷）。全部 I/O 经 {@link IEssentiaInterfaceLogic} 委托逻辑侧，网络 I/O
 * （缓冲 / 记账 / 卡门控）零散落在本类。
 * <p>
 * {@code simulate*} 与对应 commit 经桥接面严格一致，以满足 {@code EssentiaTransfer.move} 的「模拟-提交」硬契约。
 * 全程卡门控：未插源质访问卡则一行不干预原版接口。
 */
@Mixin(value = InterfaceBlockEntity.class, remap = false)
public abstract class MixinInterfaceBlockEntity implements EssentiaTransport {

    /** 目标类自身声明的 {@code getInterfaceLogic()}（:112，@Override 覆写 InterfaceLogicHost）——@Shadow 合法。 */
    @Shadow
    public abstract InterfaceLogic getInterfaceLogic();

    /** 逻辑侧桥接面（全部源质 I/O 的落点）。 */
    @Unique
    private IEssentiaInterfaceLogic thE$logic() {
        return (IEssentiaInterfaceLogic) this.getInterfaceLogic();
    }

    @Override
    public boolean isConnectable(Direction side) {
        // 恒定连通：无卡时吸力/收支全为惰性值，管道连接无害；避免插卡后才需重扫连接状态
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return thE$logic().thE$essentiaActive();
    }

    @Override
    public boolean canOutputTo(Direction side) {
        // 拉取响应开放：对外只暴露缓存格（stock 槽）内容，不暴露网络存量
        return thE$logic().thE$essentiaActive();
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {
        // 零吸力：不锁定类型，忽略外部设置
    }

    @Override
    public Aspect getSuctionType(Direction side) {
        return null; // 零吸力下通配无意义且无害（与供应器 T-M1-018 同口径）
    }

    @Override
    public int getSuctionAmount(Direction side) {
        return 0; // 零吸力：管道永不路由向接口，仅相邻有吸力机器可拉取
    }

    @Override
    public Aspect getEssentiaType(Direction side) {
        return thE$logic().thE$stockAspect();
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        Aspect aspect = thE$logic().thE$stockAspect();
        return aspect == null ? 0 : thE$logic().thE$stockAmount(aspect);
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) {
        if (!thE$logic().thE$essentiaActive() || !canOutputTo(side)) {
            return 0;
        }
        return thE$logic().thE$takeFromStock(aspect, amount);
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        if (!thE$logic().thE$essentiaActive() || !canInputFrom(side)) {
            return 0;
        }
        return thE$logic().thE$acceptEssentia(aspect, amount);
    }

    @Override
    public int simulateTakeEssentia(Aspect aspect, int amount, Direction side) {
        if (!thE$logic().thE$essentiaActive() || !canOutputTo(side)) {
            return 0;
        }
        return thE$logic().thE$simulateTakeFromStock(aspect, amount);
    }

    @Override
    public int simulateAddEssentia(Aspect aspect, int amount, Direction side) {
        if (!thE$logic().thE$essentiaActive() || !canInputFrom(side)) {
            return 0;
        }
        return thE$logic().thE$simulateAcceptEssentia(aspect, amount);
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }
}
