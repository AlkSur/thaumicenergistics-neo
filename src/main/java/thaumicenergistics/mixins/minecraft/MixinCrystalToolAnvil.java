package thaumicenergistics.mixins.minecraft;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics.common.items.ThaumicCrystalTools;

/** Keep native naming/enchantment merging, but do not repair crystal tools in an anvil. */
@Mixin(AnvilMenu.class)
public abstract class MixinCrystalToolAnvil {
    @Inject(method = "createResult", at = @At("RETURN"))
    private void the$preserveDamage(CallbackInfo ci) {
        var menu = (AnvilMenu) (Object) this;
        var input = menu.getSlot(0).getItem();
        var output = menu.getSlot(2).getItem();
        if (input.getItem() instanceof ThaumicCrystalTools.Repairable && output.is(input.getItem())
                && output.getDamageValue() < input.getDamageValue()) output.setDamageValue(input.getDamageValue());
    }
}
