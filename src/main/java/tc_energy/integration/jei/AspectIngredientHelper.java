package tc_energy.integration.jei;

import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;

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
        // 本地化显示名（中文环境显示中文），与 AE 终端内源质名称一致
        return AspectApi.tooltipName(ingredient.aspect()).getString();
    }

    @Override
    public Object getUid(AspectIngredient ingredient, UidContext context) {
        if (ingredient == null || ingredient.aspect() == null) return "thaumcraft:unknown";
        return ingredient.aspect().value().toString();
    }

    /**
     * 当前编译的 JEI 19.x 中此方法为抽象（decompile 显示新版已改为 default getUid 委托，
     * 并将本方法标记 forRemoval）。保留实现以通过编译；升级 JEI 后删除本方法即可。
     */
    @Override
    @SuppressWarnings("removal")
    public String getUniqueId(AspectIngredient ingredient, UidContext context) {
        if (ingredient == null || ingredient.aspect() == null) return "thaumcraft:unknown";
        return ingredient.aspect().value().toString();
    }

    @Override
    public ResourceLocation getResourceLocation(AspectIngredient ingredient) {
        if (ingredient == null || ingredient.aspect() == null) return ResourceLocation.withDefaultNamespace("air");
        return ingredient.aspect().value();
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
        if (ingredient.aspect() == null) return "Null AspectId";
        return "Aspect: " + ingredient.aspect().serialized();
    }

    @Override
    public String getDisplayModId(AspectIngredient ingredient) {
        return "thaumcraft";
    }
}
