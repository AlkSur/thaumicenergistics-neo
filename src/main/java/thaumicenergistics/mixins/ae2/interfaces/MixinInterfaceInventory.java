package thaumicenergistics.mixins.ae2.interfaces;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.integration.appeng.InterfaceEssentiaInventory;

/** Interface-local policy on the original inventory, including subclass proxies. */
@Mixin(value = GenericStackInv.class, remap = false)
public abstract class MixinInterfaceInventory implements InterfaceEssentiaInventory {
    @Shadow @Final protected GenericStack[] stacks;
    @Shadow @Final protected GenericStackInv.Mode mode;
    @Shadow public abstract void onChange();

    @Unique private boolean thE$interfaceInventory;

    @Override
    public void thE$markInterfaceInventory() {
        thE$interfaceInventory = true;
    }

    @Inject(method = "getCapacity", at = @At("HEAD"), cancellable = true)
    private void thE$essentiaCapacity(AEKeyType type, CallbackInfoReturnable<Long> cir) {
        if (thE$interfaceInventory && type == AEssentiaKeyType.INSTANCE) {
            cir.setReturnValue(MAX_ESSENTIA_PER_SLOT);
        }
    }

    @Inject(method = "setStack", at = @At("HEAD"), cancellable = true)
    private void thE$preserveLegacyRemainder(int slot, @Nullable GenericStack stack, CallbackInfo ci) {
        if (!thE$interfaceInventory || mode != GenericStackInv.Mode.STORAGE || stack == null
                || !(stack.what() instanceof AEssentiaKey)) return;
        // Read the physical backing array, never a virtual getStack projection.
        var previous = stacks[slot];
        if (previous != null && previous.what().equals(stack.what())
                && previous.amount() > MAX_ESSENTIA_PER_SLOT && stack.amount() > MAX_ESSENTIA_PER_SLOT
                && stack.amount() <= previous.amount()) {
            if (!previous.equals(stack)) {
                stacks[slot] = stack;
                onChange();
            }
            ci.cancel();
        }
    }

    @Inject(method = "readFromList", at = @At("HEAD"), cancellable = true)
    private void thE$preserveStorageSnapshot(List<@Nullable GenericStack> contents, CallbackInfo ci) {
        if (!thE$interfaceInventory || mode != GenericStackInv.Mode.STORAGE) return;
        boolean changed = false;
        for (int slot = 0; slot < stacks.length; slot++) {
            var stack = slot < contents.size() ? contents.get(slot) : null;
            if (stack == null || !(stack.what() instanceof AEssentiaKey)) {
                // Keep native filters, limits and callbacks for all other resources.
                ((GenericStackInv) (Object) this).setStack(slot, stack);
                continue;
            }
            if (!Objects.equals(stacks[slot], stack)) {
                stacks[slot] = stack;
                changed = true;
            }
        }
        if (changed) onChange();
        ci.cancel();
    }
}
