package thaumicenergistics.mixins.compat.ae2lt;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.moakiee.ae2lt.logic.provider.ProviderTarget;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.common.integration.appeng.ProviderEssentiaTargets;

/** 复用 AE2LT 原生批量调度及余量队列，只扩展物理目标解析出口。 */
@Mixin(value = ProviderTarget.class, remap = false)
public abstract class MixinOverloadedProviderTarget {
    @Inject(method = "resolveStorageTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Direction;Lappeng/api/networking/security/IActionSource;)Lappeng/helpers/patternprovider/PatternProviderTarget;", at = @At("RETURN"), cancellable = true)
    private void thE$target(ServerLevel level, Direction face, IActionSource source,
            CallbackInfoReturnable<PatternProviderTarget> cir) {
        var target = (ProviderTarget) (Object) this;
        cir.setReturnValue(ProviderEssentiaTargets.fromSource(cir.getReturnValue(), source,
                target.resolveBlockEntity(level), face));
    }
}
