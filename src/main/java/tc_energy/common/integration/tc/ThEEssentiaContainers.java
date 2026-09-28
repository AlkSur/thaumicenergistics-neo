package tc_energy.common.integration.tc;

import net.minecraft.world.item.ItemStack;

import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.FilledJarEssentiaApi;
import dev.tc4port.thaumcraft.item.WardedJarBlockItem;

/**
 * 源质容器判定（Ren 版 TC4）：要素安瓿（essence_phial，容量 8）与要素罐子（warded_jar，容量 64）
 * 才算"源质容器"，参与右键存入/装罐。
 * <p>
 * Ren 版统一用 {@link EssentiaContainerApi#capacity(ItemStack)} 判定：
 * 安瓿与罐子之外（天域之华 wisp_essence、水晶 crystal_essence 等）容量为 0，一律不参与，
 * 避免把非容器物品整包抽干。空玻璃安瓿与装满安瓿在 Ren 版是同一注册物品
 * （{@code thaumcraft:essence_phial}），内容用 ASPECTS 数据组件区分，不再有
 * {@code phial_of_essentia_<aspect>} 一族物品。
 */
public final class ThEEssentiaContainers {

    private ThEEssentiaContainers() {}

    /** 要素安瓿（空或满，容量 8；Ren 版统一注册 id essence_phial）。 */
    public static boolean isPhial(ItemStack stack) {
        return !stack.isEmpty() && EssentiaContainerApi.capacity(stack) == 8;
    }

    /** 要素罐子（容量 64）。 */
    public static boolean isJar(ItemStack stack) {
        return !stack.isEmpty() && WardedJarBlockItem.isJar(stack);
    }

    /** 是否源质容器（安瓿或罐子）—— 右键存入/装罐的唯一判定。 */
    public static boolean isEssentiaContainer(ItemStack stack) {
        return EssentiaContainerApi.capacity(stack) > 0;
    }

    /** 容器是否装有源质。空安瓿/空罐恒为 false；非容器物品恒为 false。 */
    public static boolean hasEssentia(ItemStack stack) {
        if (!isEssentiaContainer(stack)) {
            return false;
        }
        return !EssentiaContainerApi.contents(stack).amounts().isEmpty();
    }

    /** TC 罐子物品直查（FilledJarEssentiaApi 视角，兼容旧调用点）。 */
    public static boolean isFilledJar(ItemStack stack) {
        return FilledJarEssentiaApi.isJar(stack);
    }
}
