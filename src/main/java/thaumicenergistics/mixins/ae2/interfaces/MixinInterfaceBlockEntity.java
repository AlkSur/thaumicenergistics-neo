package thaumicenergistics.mixins.ae2.interfaces;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;

/**
 * 将 ME 接口方块接入 TC 源质管网，操作委托给接口逻辑。
 * TC port.261 按 common EssentiaTransport 寻址；仅暴露接口缓存，不直接暴露网络库存。
 */
@Mixin(value = InterfaceBlockEntity.class, remap = false)
public abstract class MixinInterfaceBlockEntity implements EssentiaTransport {

    /** 目标类自身声明的 {@code getInterfaceLogic}（:112，@Override 覆写 InterfaceLogicHost）——@Shadow 合法。 */
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
        return null; // 零吸力下通配无意义且无害（与供应器 同口径）
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
