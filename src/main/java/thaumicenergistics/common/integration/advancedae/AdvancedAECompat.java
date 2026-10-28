package thaumicenergistics.common.integration.advancedae;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.upgrades.Upgrades;

import net.neoforged.fml.ModList;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModItems;

/**
 * AdvancedAE 可选兼容：源质访问卡准入其高级样板供应器（方块/部件双形态、
 * 两种容量一起覆盖，避免玩家升级供应器后卡失效）。
 * <p>
 * AAE 的 {@code AdvPatternProviderLogic} 为独立实现（不继承 AE2 原版供应器逻辑），
 * 本类仅完成升级卡准入注册；卡槽组与源质能力的 Mixin 由独立兼容配置另行挂载。
 * 零 AAE 编译引用，ID 缺失时输出日志并跳过。
 */
public final class AdvancedAECompat {

    /** AdvancedAE modid。 */
    public static final String MODID = "advanced_ae";

    private AdvancedAECompat() {
    }

    public static void registerEssentiaUpgrades() {
        if (!ModList.get().isLoaded(MODID)) {
            return;
        }

        Item card = ModItems.ESSENTIA_UPGRADE_CARD.get();

        // 高级扩展样板供应器（大容量）：方块键统一 part/block 提示分组。
        registerHost(card, "adv_pattern_provider",
                "block.advanced_ae.adv_pattern_provider");
        registerHost(card, "adv_pattern_provider_part",
                "block.advanced_ae.adv_pattern_provider");
        // 高级样板供应器（小容量）。
        registerHost(card, "small_adv_pattern_provider",
                "block.advanced_ae.small_adv_pattern_provider");
        registerHost(card, "small_adv_pattern_provider_part",
                "block.advanced_ae.small_adv_pattern_provider");
    }

    private static void registerHost(Item card, String path, String tooltipGroup) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, path);

        Item host = BuiltInRegistries.ITEM.getOptional(id).orElse(null);

        if (host == null || host == Items.AIR) {
            ThaumicEnergistics.LOG.warn(
                    "Skipping AdvancedAE essentia upgrade registration: missing host item {}", id);
            return;
        }

        Upgrades.add(card, host, 1, tooltipGroup);
    }
}
