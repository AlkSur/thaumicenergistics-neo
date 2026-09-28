package tc_energy.integration.jei;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import mezz.jei.api.ingredients.IIngredientType;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import tc_energy.common.integration.appeng.AEssentiaKey;

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
            AspectId aspect = AspectId.of(essentiaKey.getId());
            if (AspectApi.contains(aspect)) {
                return new AspectIngredient(aspect);
            }
        }
        return null;
    }

    @Override
    public @Nullable GenericStack getStackFromIngredient(AspectIngredient ingredient) {
        if (ingredient == null || ingredient.aspect() == null) return null;
        if (!AspectApi.contains(ingredient.aspect())) return null;
        return new GenericStack(AEssentiaKey.of(ingredient.aspect().value()), 1);
    }
}
