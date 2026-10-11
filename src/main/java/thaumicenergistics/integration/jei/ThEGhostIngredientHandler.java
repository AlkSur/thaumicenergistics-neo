package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.FakeSlot;
import appeng.client.gui.AEBaseScreen;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 处理从 JEI 拖拽到 AE2 Screen 的筛选槽。
 * 支持两种拖拽方式：
 * 1. 拖拽 WrappedGenericStack ItemStack（AE2 原生支持）
 * 2. 拖拽 AspectIngredient（自定义成分类型）
 */
public class ThEGhostIngredientHandler implements IGhostIngredientHandler<Screen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(Screen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();

        if (!(screen instanceof AEBaseScreen<?> aeScreen)) {
            return targets;
        }

        if (!(aeScreen.getMenu() instanceof AEBaseMenu menu)) {
            return targets;
        }

        if (!canHandleIngredient(ingredient)) {
            return targets;
        }

        for (var slot : menu.slots) {
            if (slot instanceof FakeSlot fakeSlot) {
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(
                                aeScreen.getGuiLeft() + fakeSlot.x,
                                aeScreen.getGuiTop() + fakeSlot.y,
                                16, 16
                        );
                    }

                    @Override
                    public void accept(I ingredient) {
                        handleIngredientDrop(ingredient, fakeSlot);
                    }
                });
            }
        }

        return targets;
    }

    private <I> boolean canHandleIngredient(ITypedIngredient<I> ingredient) {
        if (ingredient.getIngredient() instanceof AspectIngredient) {
            return true;
        }
        if (ingredient.getIngredient() instanceof ItemStack stack) {
            return GenericStack.unwrapItemStack(stack) != null;
        }
        return false;
    }

    private <I> void handleIngredientDrop(I ingredient, FakeSlot fakeSlot) {
        ItemStack targetStack = ItemStack.EMPTY;

        if (ingredient instanceof AspectIngredient ai && ai.aspect() != null) {
            // AspectIngredient → WrappedGenericStack ItemStack
            var aspectId = TCReflection.getAspectId(ai.aspect());
            if (aspectId != null) {
                var key = AEssentiaKey.of(aspectId);
                targetStack = GenericStack.wrapInItemStack(key, 0);
            }
        } else if (ingredient instanceof ItemStack stack) {
            // WrappedGenericStack ItemStack（AE2 原生支持，直接放入）
            if (GenericStack.unwrapItemStack(stack) != null) {
                targetStack = stack;
            }
        }

        if (!targetStack.isEmpty()) {
            // 走 AE2 原生 SET_FILTER 路径（InventoryActionPacket，服务端执行）；
            // 不能用 FakeSlot#set（纯客户端直写，服务端不同步 → 无效写入）。
            fakeSlot.setFilterTo(targetStack);
        }
    }

    @Override
    public void onComplete() {
    }
}
