package thaumicenergistics.mixins.ae2.provider;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;


import thaumicenergistics.common.integration.appeng.ProviderEssentiaTargets;

/**
 * 在原版供应器的目标解析出口接入源质卡能力，普通推送和余量重试共用同一出口。
 * 已持有资源的交付、能量结算及物品/源质分流委托给公共目标适配层。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class MixinPatternProviderLogicEssentia {

    /** 宿主（Part 或 BlockEntity），声明于本类自身（PatternProviderLogic.java:96）。 */
    @Final
    @Shadow
    private PatternProviderLogicHost host;

    /** 网格节点，声明于本类自身（PatternProviderLogic.java:97）。 */
    @Final
    @Shadow
    private IManagedGridNode mainNode;

    /** 供应器自身的操作源（PatternProviderLogic.java:98，MachineSource），供网络抽取记账用。 */
    @Final
    @Shadow
    private IActionSource actionSource;

    /**
     * 挂在 {@code findAdapter} RETURN：卡在装且相邻 BE 为 TC 源质端点时，把返回 target
     * 换成/包装成源质感知实现；否则一行不干预（无卡 = 原版供应器）。
     * <p>
     * 推送失败重试由 AE2 原生链路承载（未注入量 → addToSendList → sendStacksOut），
     * 本注入点无状态、每 tick 重入安全。
     */
    @Inject(method = "findAdapter", at = @At("RETURN"), cancellable = true)
    private void thE$essentiaPushTarget(Direction side, CallbackInfoReturnable<PatternProviderTarget> cir) {
        if (!ProviderEssentiaTargets.hasCard(this)) return;
        var owner = host.getBlockEntity();
        if (owner == null || !(owner.getLevel() instanceof ServerLevel level)) return;
        var pos = owner.getBlockPos().relative(side);
        if (!level.isLoaded(pos)) return;
        cir.setReturnValue(ProviderEssentiaTargets.wrap(cir.getReturnValue(), mainNode.getGrid(), actionSource,
                level.getBlockEntity(pos), side.getOpposite()));
    }

}
