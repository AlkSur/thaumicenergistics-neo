package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import net.neoforged.neoforge.network.PacketDistributor;

import thaumicenergistics.common.container.GuiDistillationPatternEncoder;
import thaumicenergistics.common.network.DistillationEncoderSetSourceC2SPacket;

/**
 * 蒸馏编码台 JEI 拖拽：从 JEI 拉取物品到源物品槽（15,69）→ C2S 设置源物品。
 */
public class DistillationEncoderGhostIngredientHandler
        implements IGhostIngredientHandler<GuiDistillationPatternEncoder> {

    /** 与 ContainerDistillationPatternEncoder.SLOT_SOURCE_X/Y 一致（15, 69）。 */
    private static final int SOURCE_SLOT_X = 15;
    private static final int SOURCE_SLOT_Y = 69;

    @Override
    public <I> List<Target<I>> getTargetsTyped(GuiDistillationPatternEncoder gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }
        int wx = gui.getGuiLeft() + SOURCE_SLOT_X;
        int wy = gui.getGuiTop() + SOURCE_SLOT_Y;
        targets.add(new Target<>() {
            @Override
            public Rect2i getArea() {
                return new Rect2i(wx, wy, 16, 16);
            }

            @Override
            public void accept(I ingredient) {
                if (ingredient instanceof ItemStack stack) {
                    PacketDistributor.sendToServer(new DistillationEncoderSetSourceC2SPacket(
                            gui.getMenu().containerId, stack.copyWithCount(1)));
                }
            }
        });
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
