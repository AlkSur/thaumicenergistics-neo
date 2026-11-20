package thaumicenergistics.integration.jei;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;

/** AE2-JEI-Integration converter for the shared aspect ingredient of scex_aspect_jei (TEL-01). */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SharedAspectIngredientConverter implements IngredientConverter<Object> {
    private final IIngredientType type;

    public SharedAspectIngredientConverter(IIngredientType<?> type) {
        this.type = type;
    }

    @Override
    public IIngredientType<Object> getIngredientType() {
        return type;
    }

    @Override
    public @Nullable Object getIngredientFromStack(GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEssentiaKey key)) return null;
        var aspect = AspectIds.findAspectById(key.getId());
        return aspect == null ? null : JeiAspects.ingredientFor(aspect);
    }

    @Override
    public @Nullable GenericStack getStackFromIngredient(Object ingredient) {
        var aspect = JeiAspects.aspectOf(ingredient);
        var id = aspect == null ? null : AspectIds.getAspectId(aspect);
        return id == null ? null : new GenericStack(AEssentiaKey.of(id), 1);
    }
}
