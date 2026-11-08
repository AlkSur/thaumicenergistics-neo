package thaumicenergistics.mixins.tc;

import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import thaumcraft.common.research.ResearchPageIndex;

@Mixin(value = ResearchPageIndex.class, remap = false)
public abstract class ResearchPageIndexMixin {

    @Redirect(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;equals(Ljava/lang/Object;)Z"
            )
    )
    private static boolean fixEquals(Set instance, Object other) {
        if (other instanceof Set<?> indexSet) {
            return indexSet.containsAll(instance);
        }
        return instance.equals(other);
    }
}