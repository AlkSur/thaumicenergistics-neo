package thaumicenergistics.common.integration.appeng;

import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

import thaumicenergistics.common.essentia.capability.EssentiaTransfer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * T-M2-002（P-A 裁决）：样板供应器<b>订单驱动源质输出</b>的推送落点（纯输出侧）。
 * <p>
 * <b>为什么复用导出总线范式</b>：TC {@code IEssentiaTransport.simulateAddEssentia} 的 default
 * 实现恒返 0（字节码实证：iconst_0，仅管道/储罐/结晶器覆写），若按字面 simulate/add 配对，
 * 罐子/蒸馏器等大半 TC 容器会被误判「不可接收」。故本类与 {@link EssentiaTransfer#push} 同范式：
 * MODULATE 直接走导出总线落点（IAspectContainer 优先 + addEssentia 回退），
 * SIMULATE 采用<b>类型级近似</b>（{@code simulateAddEssentia > 0}，拉取式机器回退
 * {@code isConnectable && canInputFrom}，容器类回退 {@code doesContainerAccept}，
 * <b>不验余量</b>）——余量缺口由 AE2 原生 sendList 重试兜底，
 * 验收口径为「下单后源质最终到位」。
 * <p>
 * <b>为什么不在 mixin 包</b>：本类是普通运行期类（由注入体直接 new），Mixin 框架禁止
 * mixin 声明包内类被普通代码直接引用（IllegalClassLoadError），故落 AE2 集成包——
 * 与 {@code IEssentiaInterfaceLogic}（T-M2-001 桥接接口）同一分层先例。
 * <p>
 * <b>为什么不收物品/流体</b>：源质交互分工制约定供应器 = 纯输出侧的源质通道；
 * 物品/流体产物回收仍走 AE2 原生（机器推回供应器），源质落点只认 {@link AEssentiaKey}。
 */
public final class ThEEssentiaPushTarget implements PatternProviderTarget {

    private final IGrid grid;
    private final IActionSource source;
    private final BlockEntity container;
    /** 容器接收面的朝向（TC 管道 addEssentia 回退路径需要）。 */
    private final Direction accessSide;

    public ThEEssentiaPushTarget(IGrid grid, IActionSource source, BlockEntity container, Direction accessSide) {
        this.grid = grid;
        this.source = source;
        this.container = container;
        this.accessSide = accessSide;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable type) {
        if (!(what instanceof AEssentiaKey key) || amount <= 0) {
            return 0; // 源质落点不收物品/流体（回收走 AE2 原生机器推回链路）
        }
        if (type == Actionable.SIMULATE) {
            // P-A 类型级近似：不验余量，只要容器「接受该类型」即放行推送；
            // 实际注入不足的部分由 pushPattern 的 addToSendList → sendStacksOut 原生重试
            return thE$typeAccepts(key) ? amount : 0;
        }
        // 与导出总线同款落点：IAspectContainer 优先 + addEssentia 回退；未注入部分退回网络，绝不丢料
        long injected = EssentiaTransfer.push(this.grid, this.source, key, amount, this.container, this.accessSide);
        if (injected > 0) {
            // 按实际搬运量 double 记账（禁止 (int) 截断），口径与导出总线 TRANSFER_POWER 一致
            this.grid.getEnergyService().extractAEPower(
                    injected * EssentiaConversionHelper.AE_PER_ESSENTIA,
                    Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        return injected;
    }

    /**
     * blocking 语义：仅当相邻机器对外广告了某个源质输入键（机内已有所需源质）时拦推。
     * 广告查询出自 {@link TCReflection#listEssentia}；查询异常时<b>降级放行</b>（返 false），
     * 误推的余量由 sendList 兜底，不会丢料。
     */
    @Override
    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        try {
            for (var advertised : TCReflection.listEssentia(this.container)) {
                for (var key : patternInputs) {
                    if (key instanceof AEssentiaKey essentiaKey
                            && advertised.aspectId().equals(essentiaKey.getId())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // 遇阻降级：判不出就放行推送（记录于交付报告）
        }
        return false;
    }

    /**
     * 类型级接收判定（P-A）：{@code simulateAddEssentia > 0} 优先；拉取式机器（实现
     * transport 但不覆写 simulate——如神秘炼金塔 Thaumatorium，配方激活时 addEssentia
     * 实收）以机器自身 {@code isConnectable && canInputFrom} 作类型级放行；容器类回退
     * {@code IAspectContainer.doesContainerAccept}。不验余量；MODULATE 收 0 的空转
     * 由 AE2 sendList 原生重试兜底（不丢料）。
     */
    private boolean thE$typeAccepts(AEssentiaKey key) {
        Aspect aspect = TCReflection.findAspectById(key.getId());
        if (aspect == null) {
            return false;
        }
        if (this.container instanceof IEssentiaTransport transport
                && (transport.simulateAddEssentia(aspect, 1, this.accessSide) > 0
                        || (transport.isConnectable(this.accessSide)
                                && transport.canInputFrom(this.accessSide)))) {
            return true;
        }
        return this.container instanceof IAspectContainer aspectContainer
                && aspectContainer.doesContainerAccept(aspect);
    }
}
