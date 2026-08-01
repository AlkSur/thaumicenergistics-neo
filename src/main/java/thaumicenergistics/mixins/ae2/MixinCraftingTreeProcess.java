package thaumicenergistics.mixins.ae2;
import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.injection.At; import org.spongepowered.asm.mixin.injection.Inject; import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "appeng.crafting.CraftingTreeProcess", remap = false)
public abstract class MixinCraftingTreeProcess {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onConstructed(CallbackInfo ci) {}
}
