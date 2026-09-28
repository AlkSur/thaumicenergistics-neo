package tc_energy.integration.jei;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.Rect2i;

import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.core.definitions.AEItems;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import tc_energy.common.integration.appeng.AEssentiaKey;

/**
 * AE 界面（源质终端等）的 JEI GuiContainerHandler：
 * 悬停仓库中的源质条目时，返回 {@link AspectIngredient} 作为可点击成分，
 * 使 JEI 的 A 键收藏/配方查看能正确显示源质图标与名称。
 * <p>
 * 背景：源质是非物品/流体 AEKey，repo 中显示为 WrappedGenericStack 物品。
 * 若不提供转换，JEI 收藏会退化为"包裹通用堆栈"（无图标、名字丢失）。
 * 本处理不依赖 AE2-JEI-Integration 是否存在，ThE 自带即可生效。
 */
public class ThEEssentiaGuiHandler implements IGuiContainerHandler<AEBaseScreen<?>> {

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory factory, AEBaseScreen<?> screen, double mouseX, double mouseY) {
        StackWithBounds stackWithBounds = screen.getStackUnderMouse(mouseX, mouseY);
        if (stackWithBounds == null) {
            return Optional.empty();
        }

        AEssentiaKey key = unwrapEssentiaKey(stackWithBounds.stack());
        if (key == null) {
            return Optional.empty();
        }

        // AEssentiaKey id → AspectId（Ren 版注册表校验）
        AspectId aspect = AspectId.of(key.getId());
        if (!AspectApi.contains(aspect)) {
            return Optional.empty();
        }

        return factory.createBuilder(AspectIngredientType.INSTANCE, new AspectIngredient(aspect))
                .buildWithArea(stackWithBounds.bounds());
    }

    @Override
    public List<Rect2i> getGuiExtraAreas(AEBaseScreen<?> screen) {
        return List.of();
    }

    /**
     * 从 GenericStack 中解析源质 key，兼容两种形式：
     * <ul>
     *   <li>直接 {@link AEssentiaKey}（StackWithBounds.fromSlot 已 unwrap 的形式）</li>
     *   <li>{@code WrappedGenericStack} 物品包裹的形式（unwrap 失败/直接拿到物品的形式）</li>
     * </ul>
     */
    private static AEssentiaKey unwrapEssentiaKey(GenericStack stack) {
        if (stack == null) {
            return null;
        }
        AEKey what = stack.what();
        if (what instanceof AEssentiaKey essentiaKey) {
            return essentiaKey;
        }
        if (what instanceof AEItemKey itemKey) {
            var itemStack = itemKey.toStack();
            if (AEItems.WRAPPED_GENERIC_STACK.is(itemStack)) {
                var unwrapped = GenericStack.unwrapItemStack(itemStack);
                if (unwrapped != null && unwrapped.what() instanceof AEssentiaKey wrappedKey) {
                    return wrappedKey;
                }
            }
        }
        return null;
    }
}
