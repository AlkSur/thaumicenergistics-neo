package thaumicenergistics.mixins.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import thaumicenergistics.client.ResearchGateOverlay;

/**
 * 拦截 CraftConfirmScreen 打开 CPU 忙错误屏。
 *
 * 研究未解锁拦截会触发 AE2 的 CPU_BUSY 错误屏；本 Mixin 在错误屏被 switchToScreen 之前
 * （updateBeforeRender HEAD）检测到研究拦截（ResearchGateOverlay 已有网络包写入的研究键），
 * 取消原渲染逻辑，避免打开误导性的"CPU 忙"窗口。
 * 提示文字由 ResearchGateOverlay 在无 GUI 遮挡时绘制。
 */
@Mixin(targets = "appeng.client.gui.me.crafting.CraftConfirmScreen", remap = false)
public abstract class MixinCraftConfirmScreen {

    @Inject(method = "updateBeforeRender()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void thaumicenergistics$blockCpuBusyScreen(CallbackInfo ci) {
        // 网络包已写入研究键 = 本次失败是研究拦截，而非真实 CPU 忙碌
        // 使用 submitError 状态而非时间戳：只要错误未被玩家清除就持续拦截，避免过期后弹窗
        if (ResearchGateOverlay.hasResearchGateError()) {
            ci.cancel();
        }
    }
}
