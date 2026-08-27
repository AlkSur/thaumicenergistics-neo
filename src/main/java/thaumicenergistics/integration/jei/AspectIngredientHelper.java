package thaumicenergistics.integration.jei;

import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.tc.TCReflection;

@SuppressWarnings("removal")
public class AspectIngredientHelper implements IIngredientHelper<AspectIngredient> {

    public static final AspectIngredientHelper INSTANCE = new AspectIngredientHelper();

    private AspectIngredientHelper() {}

    @Override
    public AspectIngredientType getIngredientType() {
        return AspectIngredientType.INSTANCE;
    }

    @Override
    public String getDisplayName(AspectIngredient ingredient) {
        if (ingredient == null || ingredient.aspect() == null) return "Unknown";
        // 用本地化显示名（displayName，中文环境显示中文），与 AE 终端内源质名称一致
        return ingredient.aspect().displayName().getString();
    }

    @Override
    public Object getUid(AspectIngredient ingredient, UidContext context) {
        if (ingredient == null || ingredient.aspect() == null) return "thaumcraft:unknown";
        ResourceLocation id = TCReflection.getAspectId(ingredient.aspect());
        return id != null ? id.toString() : "thaumcraft:" + ingredient.aspect().getTag();
    }

    @Override
    public String getUniqueId(AspectIngredient ingredient, UidContext context) {
        return getUid(ingredient, context).toString();
    }

    @Override
    public ResourceLocation getResourceLocation(AspectIngredient ingredient) {
        if (ingredient == null || ingredient.aspect() == null) return ResourceLocation.withDefaultNamespace("air");
        ResourceLocation id = TCReflection.getAspectId(ingredient.aspect());
        return id != null ? id : ResourceLocation.withDefaultNamespace(ingredient.aspect().getTag());
    }

    @Override
    public AspectIngredient copyIngredient(AspectIngredient ingredient) {
        return ingredient;
    }

    @Override
    public boolean isValidIngredient(AspectIngredient ingredient) {
        return ingredient != null && ingredient.aspect() != null;
    }

    @Override
    public boolean isIngredientOnServer(AspectIngredient ingredient) {
        return true;
    }

    @Override
    public String getErrorInfo(AspectIngredient ingredient) {
        if (ingredient == null) return "Null AspectIngredient";
        if (ingredient.aspect() == null) return "Null Aspect";
        return "Aspect: " + ingredient.aspect().getTag();
    }

    @Override
    public String getDisplayModId(AspectIngredient ingredient) {
        return "thaumcraft";
    }
}
