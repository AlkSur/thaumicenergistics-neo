package thaumicenergistics.integration.jei;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import mezz.jei.api.ingredients.IIngredientType;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 源质转换器：GenericStack(AEssentiaKey) ↔ AspectIngredient。
 * 通过 ServiceLoader 自动注册到 AE2-JEI-Integration。
 * 实现从 JEI 拖拽源质到 AE2 筛选槽的功能。
 */
public class EssentiaIngredientConverter implements IngredientConverter<AspectIngredient> {

    @Override
    public IIngredientType<AspectIngredient> getIngredientType() {
        return AspectIngredientType.INSTANCE;
    }

    @Override
    public @Nullable AspectIngredient getIngredientFromStack(GenericStack stack) {
        if (stack == null) return null;
        AEKey what = stack.what();
        if (what instanceof AEssentiaKey essentiaKey) {
            ResourceLocation id = essentiaKey.getId();
            Aspect aspect = Aspect.getAspect(id.getPath());
            if (aspect != null) {
                return new AspectIngredient(aspect);
            }
        }
        return null;
    }

    @Override
    public @Nullable GenericStack getStackFromIngredient(AspectIngredient ingredient) {
        if (ingredient == null || ingredient.aspect() == null) return null;
        var aspectId = TCReflection.getAspectId(ingredient.aspect());
        if (aspectId == null) return null;
        return new GenericStack(AEssentiaKey.of(aspectId), 1);
    }
}
