package tc_energy.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import tc_energy.common.container.GuiEssentiaCellWorkbench;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.common.network.CellWorkbenchC2SPacket;

/**
 * 源质元件工作台分区格的 JEI 拖拽支持：从 JEI 拖拽要素到 63 个分区格之一 → 添加该要素分区。
 * 纯 JEI API 实现，不依赖 AE2-JEI-Integration（只装 JEI 即可用）。
 */
public class CellWorkbenchGhostIngredientHandler implements IGhostIngredientHandler<GuiEssentiaCellWorkbench> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(GuiEssentiaCellWorkbench gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof AspectIngredient)) {
            return targets;
        }
        // 63 个分区格都是拖放目标
        for (int i = 0; i < GuiEssentiaCellWorkbench.NUMBER_OF_WIDGETS; i++) {
            int col = i % GuiEssentiaCellWorkbench.WIDGETS_PER_ROW;
            int row = i / GuiEssentiaCellWorkbench.WIDGETS_PER_ROW;
            int wx = gui.getGuiLeft() + GuiEssentiaCellWorkbench.WIDGET_POS_X + col * GuiEssentiaCellWorkbench.WIDGET_SIZE;
            int wy = gui.getGuiTop() + GuiEssentiaCellWorkbench.WIDGET_POS_Y + row * GuiEssentiaCellWorkbench.WIDGET_SIZE;
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(wx, wy, GuiEssentiaCellWorkbench.WIDGET_SIZE, GuiEssentiaCellWorkbench.WIDGET_SIZE);
                }

                @Override
                public void accept(I ingredient) {
                    if (ingredient instanceof AspectIngredient ai && ai.aspect() != null) {
                        ResourceLocation aspectId = ai.aspect().value();
                        if (aspectId != null) {
                            CellWorkbenchC2SPacket.sendAddAspect(aspectId);
                        }
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
