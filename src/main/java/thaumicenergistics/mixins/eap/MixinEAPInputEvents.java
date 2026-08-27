package thaumicenergistics.mixins.eap;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.ITypedIngredient;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.integration.jei.AspectIngredient;

/**
 * ExtendedAE-Plus 兼容（可选，eap 未安装时静默跳过）：
 * 其 {@code InputEvents.toGenericStack} 只认 ItemStack/FluidStack，
 * 我们收藏的源质（AspectIngredient）会返回 null → 中键/Shift+左键 无效。
 * 此处把源质转成 {@code GenericStack(AEssentiaKey)}，让 eap 的中键拉起合成、拉取流程正常走通。
 */
@Mixin(targets = "com.extendedae_plus.client.InputEvents", remap = false)
public abstract class MixinEAPInputEvents {

    @Inject(method = "toGenericStack(Ljava/lang/Object;)Lappeng/api/stacks/GenericStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void thE$essentiaToGenericStack(Object typed, CallbackInfoReturnable<GenericStack> cir) {
        if (!(typed instanceof ITypedIngredient<?> typedIngredient)) {
            return;
        }
        Object value = typedIngredient.getIngredient();

        AEssentiaKey key = null;
        // 新收藏：AspectIngredient（ThEEssentiaGuiHandler 提供，有图标+中文名）
        if (value instanceof AspectIngredient ai && ai.aspect() != null) {
            Aspect aspect = ai.aspect();
            var id = TCReflection.getAspectId(aspect);
            if (id != null) {
                key = AEssentiaKey.of(id);
            }
        } else if (value instanceof ItemStack itemStack) {
            // 旧收藏/兜底：WrappedGenericStack 物品包裹的形式
            GenericStack unwrapped = GenericStack.unwrapItemStack(itemStack);
            if (unwrapped != null && unwrapped.what() instanceof AEssentiaKey wrappedKey) {
                key = wrappedKey;
            }
        }

        if (key != null) {
            cir.setReturnValue(new GenericStack(key, 1));
        }
    }
}
