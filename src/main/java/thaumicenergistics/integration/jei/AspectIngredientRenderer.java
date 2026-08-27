package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import mezz.jei.api.ingredients.IIngredientRenderer;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.gui.AspectGuiRenderer;

public class AspectIngredientRenderer implements IIngredientRenderer<AspectIngredient> {

    public static final AspectIngredientRenderer INSTANCE = new AspectIngredientRenderer();

    private static final int SIZE = 16;

    private AspectIngredientRenderer() {}

    @Override
    public void render(GuiGraphics guiGraphics, AspectIngredient ingredient) {
        if (ingredient != null && ingredient.aspect() != null) {
            AspectGuiRenderer.draw(guiGraphics, ingredient.aspect(), 0, 0, SIZE, 1.0f);
        }
    }

    @Override
    public List<Component> getTooltip(AspectIngredient ingredient, TooltipFlag tooltipFlag) {
        List<Component> tooltip = new ArrayList<>();
        if (ingredient == null || ingredient.aspect() == null) {
            return tooltip;
        }
        Aspect aspect = ingredient.aspect();
        // 本地化显示名（中文环境显示中文），与 AE 终端内源质名称一致
        tooltip.add(aspect.displayName());
        tooltip.add(Component.literal(aspect.getTag()).withStyle(style -> style.withColor(0x888888)));
        return tooltip;
    }

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }
}
