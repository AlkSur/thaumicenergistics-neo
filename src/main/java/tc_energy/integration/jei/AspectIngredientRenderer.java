package tc_energy.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import mezz.jei.api.ingredients.IIngredientRenderer;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.client.AspectGuiRender;

public class AspectIngredientRenderer implements IIngredientRenderer<AspectIngredient> {

    public static final AspectIngredientRenderer INSTANCE = new AspectIngredientRenderer();

    private static final int SIZE = 16;

    private AspectIngredientRenderer() {}

    @Override
    public void render(GuiGraphics guiGraphics, AspectIngredient ingredient) {
        if (ingredient != null && ingredient.aspect() != null) {
            AspectGuiRender.blit(guiGraphics, ingredient.aspect(), 0, 0, 1.0f);
        }
    }

    @Override
    public List<Component> getTooltip(AspectIngredient ingredient, TooltipFlag tooltipFlag) {
        List<Component> tooltip = new ArrayList<>();
        if (ingredient == null || ingredient.aspect() == null) {
            return tooltip;
        }
        AspectId aspect = ingredient.aspect();
        tooltip.add(AspectApi.tooltipName(aspect));
        tooltip.add(Component.literal(aspect.serialized()).withStyle(style -> style.withColor(0x888888)));
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
