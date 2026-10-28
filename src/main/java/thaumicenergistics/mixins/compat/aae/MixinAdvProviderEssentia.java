package thaumicenergistics.mixins.compat.aae;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.common.integration.appeng.ProviderEssentiaTargets;

/** 双参数出口覆盖普通输出、定向输出与 sendList 重试。 */
@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public abstract class MixinAdvProviderEssentia {
    @Shadow @Final private AdvPatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private IActionSource actionSource;

    @Inject(method = "findAdapter(Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;)Lappeng/helpers/patternprovider/PatternProviderTarget;", at = @At("RETURN"), cancellable = true)
    private void thE$target(Direction direction, Direction face, CallbackInfoReturnable<PatternProviderTarget> cir) {
        if (!ProviderEssentiaTargets.hasCard(this)) return;
        var owner = host.getBlockEntity();
        if (owner == null || !(owner.getLevel() instanceof ServerLevel level)) return;
        var pos = owner.getBlockPos().relative(direction);
        if (!level.isLoaded(pos)) return;
        cir.setReturnValue(ProviderEssentiaTargets.wrap(cir.getReturnValue(), mainNode.getGrid(), actionSource,
                level.getBlockEntity(pos), face != null ? face : direction.getOpposite()));
    }
}
