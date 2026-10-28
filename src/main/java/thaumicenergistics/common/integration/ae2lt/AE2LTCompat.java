package thaumicenergistics.common.integration.ae2lt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.upgrades.Upgrades;

import net.neoforged.fml.ModList;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModItems;

/**
 * AE2 Lightning Tech 可选兼容：源质访问卡准入其过载样板供应器（方块与扩展过载两形态，
 * 发布 jar 2.1.2-beta3 实证仅方块形态、无部件注册）。
 * <p>
 * 其 {@code OverloadedPatternProviderLogic}/{@code OverloadedPatternProviderBlockEntity}
 * 与菜单均直接继承 AE2 原版供应器体系（字节码实证），升级卡库存、菜单升级槽与
 * 普通相邻输出的源质路径由既有注入自动生效；定向/无线/批量等独立派发路径的
 * 源质门控由独立兼容配置另行挂载。零 AE2LT 编译引用，ID 缺失时输出日志并跳过。
 */
public final class AE2LTCompat {

    /** AE2 Lightning Tech modid。 */
    public static final String MODID = "ae2lt";

    private AE2LTCompat() {
    }

    public static void registerEssentiaUpgrades() {
        if (!ModList.get().isLoaded(MODID)) {
            return;
        }

        Item card = ModItems.ESSENTIA_UPGRADE_CARD.get();

        // 过载样板供应器（基础形态）。
        registerHost(card, "overloaded_pattern_provider",
                "block.ae2lt.overloaded_pattern_provider");
        // 扩展过载样板供应器（扩展容量形态）。
        registerHost(card, "extended_overloaded_pattern_provider",
                "block.ae2lt.extended_overloaded_pattern_provider");
    }

    private static void registerHost(Item card, String path, String tooltipGroup) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, path);

        Item host = BuiltInRegistries.ITEM.getOptional(id).orElse(null);

        if (host == null || host == Items.AIR) {
            ThaumicEnergistics.LOG.warn(
                    "Skipping AE2LT essentia upgrade registration: missing host item {}", id);
            return;
        }

        Upgrades.add(card, host, 1, tooltipGroup);
    }
}
