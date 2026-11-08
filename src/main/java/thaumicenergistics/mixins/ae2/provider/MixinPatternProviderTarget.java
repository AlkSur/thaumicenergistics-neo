package thaumicenergistics.mixins.ae2.provider;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.common.integration.appeng.ProviderEssentiaTargets;

/** Shared AE2 target entry point; no dependency on any addon's dispatcher classes. */
@Mixin(value = PatternProviderTarget.class, remap = false)
public interface MixinPatternProviderTarget {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void thE$essentiaTarget(Level level, BlockPos pos, @Nullable BlockEntity blockEntity,
            Direction face, IActionSource source, CallbackInfoReturnable<PatternProviderTarget> cir) {
        if (!(level instanceof ServerLevel)) return;
        cir.setReturnValue(ProviderEssentiaTargets.fromSource(cir.getReturnValue(), source, blockEntity, face));
    }
}
