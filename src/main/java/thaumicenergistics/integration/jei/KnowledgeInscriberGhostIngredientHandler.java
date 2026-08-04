package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import net.neoforged.neoforge.network.PacketDistributor;

import thaumicenergistics.common.container.GuiKnowledgeInscriber;
import thaumicenergistics.common.container.slot.GhostSlot;
import thaumicenergistics.common.network.KnowledgeInscriberGhostSlotPacket;

/**
 * 知识记录仪 JEI 拖拽：从 JEI 拉取物品到 3×3 合成网格（ghost 槽）。
 * 复用已有的 GhostSlot + KnowledgeInscriberGhostSlotPacket（客户端 set + C2S 同步服务端）。
 */
public class KnowledgeInscriberGhostIngredientHandler
        implements IGhostIngredientHandler<GuiKnowledgeInscriber> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(GuiKnowledgeInscriber gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }
        for (var slot : gui.getMenu().slots) {
            if (slot instanceof GhostSlot ghostSlot) {
                int fx = slot.x;
                int fy = slot.y;
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(gui.getGuiLeft() + fx, gui.getGuiTop() + fy, 16, 16);
                    }

                    @Override
                    public void accept(I ingredient) {
                        if (ingredient instanceof ItemStack stack) {
                            ItemStack s = stack.copyWithCount(1);
                            ghostSlot.set(s);
                            // 与手动点击一致：客户端刷新产物计算 + C2S 同步服务端
                            if (gui.getMenu() instanceof thaumicenergistics.common.container.ContainerKnowledgeInscriber menu) {
                                menu.onCraftingChangedClient();
                            }
                            int gridIndex = gui.getMenu() instanceof thaumicenergistics.common.container.ContainerKnowledgeInscriber m
                                    ? m.getGhostGridIndex(ghostSlot) : -1;
                            PacketDistributor.sendToServer(
                                    new KnowledgeInscriberGhostSlotPacket(gui.getMenu().containerId, gridIndex, s));
                        }
                    }
                });
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
