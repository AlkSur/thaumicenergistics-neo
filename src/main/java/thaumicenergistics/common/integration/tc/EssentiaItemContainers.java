package thaumicenergistics.common.integration.tc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/** 按 TC 的真实物品组件保存剩余源质，不把部分成功假定为可回滚的事务。 */
public final class EssentiaItemContainers {
    private EssentiaItemContainers() {
    }

    public static AspectList contents(ItemStack stack) {
        if (ThEEssentiaContainers.isEssentiaContainer(stack)
                && stack.getItem() instanceof IEssentiaContainerItem container) {
            var aspects = container.getAspects(stack);
            if (aspects != null) {
                return aspects.copy();
            }
        }
        return new AspectList();
    }

    @Nullable
    public static ItemStack withContents(ItemStack original, AspectList contents) {
        ItemStack result;
        if (ThEEssentiaContainers.isJar(original)) {
            result = original.copyWithCount(1);
        } else if (ThEEssentiaContainers.isPhial(original)) {
            String path = contents.isEmpty() ? "glass_phial" : "phial_of_essentia";
            var item = BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", path)).orElse(null);
            if (item == null) {
                return null;
            }
            result = new ItemStack(item);
            result.applyComponents(original.getComponentsPatch());
        } else {
            return null;
        }
        if (result.getItem() instanceof IEssentiaContainerItem container) {
            container.setAspects(result, contents);
            var actual = contents(result);
            for (Aspect aspect : contents.getAspects()) {
                if (actual.getAmount(aspect) != contents.getAmount(aspect)) {
                    return null;
                }
            }
            if (actual.visSize() != contents.visSize()) {
                return null;
            }
        } else if (!contents.isEmpty()) {
            return null;
        }
        // Jars never change identity when filled or drained. Phials intentionally change item type.
        // Enforce this for both native AE2 container interactions and our terminal packet paths.
        if (ThEEssentiaContainers.isJar(original) && result.getItem() != original.getItem()) {
            return null;
        }
        return result;
    }
}
