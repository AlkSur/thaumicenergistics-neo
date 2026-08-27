package thaumicenergistics.common.integration.tc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.items.AspectEssenceItem;
import thaumcraft.common.items.JarBlockItem;

/**
 * 源质容器判定：只有要素安瓿与要素罐子才算"源质容器"，参与右键存入/装罐。
 * <p>
 * 不能只判 {@code IEssentiaContainerItem}：TC 里天域之华（wisp_essence）、源质水晶（CRYSTAL）
 * 等物品也实现了该接口，但它们不是容器——若不排除，右键会被当源质抽走（例如天域之华
 * 会被整个抽干变成空玻璃安瓿）。同一实现类 {@link AspectEssenceItem} 用
 * {@link AspectEssenceItem#kind()} 区分形态：
 * <ul>
 *   <li>要素安瓿（变体/自定义要素）：{@code AspectEssenceItem$Kind.PHIAL}（固定 8 源质）</li>
 *   <li>空玻璃安瓿：{@code thaumcraft:glass_phial}（普通 Item，无 Kind）</li>
 *   <li>要素罐子：{@link JarBlockItem}（TC 罐子统一类型，容量 64）</li>
 *   <li>天域之华 / 水晶：{@code Kind.WISP / Kind.CRYSTAL} —— 一律排除</li>
 * </ul>
 */
public final class ThEEssentiaContainers {

    private static final ResourceLocation GLASS_PHIAL_ID =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "glass_phial");

    private ThEEssentiaContainers() {}

    /** 要素安瓿：AspectEssenceItem(Kind.PHIAL)，或空玻璃安瓿（glass_phial 无 Kind）。 */
    public static boolean isPhial(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof AspectEssenceItem essence) {
            return essence.kind() == AspectEssenceItem.Kind.PHIAL;
        }
        return stack.getItem() == BuiltInRegistries.ITEM.get(GLASS_PHIAL_ID);
    }

    /** 要素罐子：TC 罐子统一类型（JarBlockItem）。 */
    public static boolean isJar(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof JarBlockItem;
    }

    /** 是否源质容器（安瓿或罐子）——右键存入/装罐的唯一判定。 */
    public static boolean isEssentiaContainer(ItemStack stack) {
        return isPhial(stack) || isJar(stack);
    }

    /** 容器是否装有源质。空玻璃安瓿恒为 false；天域之华/水晶等非容器恒为 false。 */
    public static boolean hasEssentia(ItemStack stack) {
        if (!isEssentiaContainer(stack)) {
            return false;
        }
        if (stack.getItem() instanceof IEssentiaContainerItem container) {
            var aspects = container.getAspects(stack);
            return aspects != null && !aspects.isEmpty();
        }
        return false;
    }
}