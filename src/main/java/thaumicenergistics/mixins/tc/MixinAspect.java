package thaumicenergistics.mixins.tc;
import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.injection.At; import org.spongepowered.asm.mixin.injection.Inject; import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "thaumcraft.api.aspects.Aspect", remap = false)
public abstract class MixinAspect {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onConstructed(CallbackInfo ci) {}
}
