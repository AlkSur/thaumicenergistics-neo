package thaumicenergistics.integration.jei;

import mezz.jei.api.ingredients.IIngredientType;

/**
 * JEI 自定义成分类型：AspectIngredient。
 */
public class AspectIngredientType implements IIngredientType<AspectIngredient> {

    public static final AspectIngredientType INSTANCE = new AspectIngredientType();

    private AspectIngredientType() {}

    @Override
    public Class<? extends AspectIngredient> getIngredientClass() {
        return AspectIngredient.class;
    }
}
