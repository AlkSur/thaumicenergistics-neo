package thaumicenergistics.common.integration.extendedae;

import appeng.api.upgrades.Upgrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModItems;

/**
 * Extended AE（extendedae）可选兼容：把源质访问卡准入注册到其扩展样板供应器与超大接口。
 * <p>
 * 为什么只做注册：EAE 两类宿主（方块/部件）的 Logic 均复用 AE2 的
 * {@code PatternProviderLogic} 与 {@code InterfaceLogic}（2.2.35 jar javap 实证），
 * 本项目对这两个 Logic 类的 Mixin（卡槽组 + 源质组）对其自动生效，
 * 唯一缺口是 {@code Upgrades.add} 宿主准入——此处补齐即可，零 EAE 编译引用。
 * <p>
 * 为什么升级库存宿主物品必然匹配：EAE 宿主的 {@code getTerminalIcon()} 覆写返回
 * EAE 自身物品（部件返回 part item、方块返回 EX_PATTERN_PROVIDER），超大接口走
 * AE2 {@code createLogic()} 传 {@code getItemFromBlockEntity()} / 构造器 partItem——
 * 与本类注册的四个 ID 一一对应，AE2 准入查询（{@code Upgrades.getMaxInstallable}）成立。
 */
public final class ThEExtendedAE {

    public static final String MOD_ID = "extendedae";

    private ThEExtendedAE() {
    }

    public static void registerEssentiaUpgrades() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }

        Item card = ModItems.ESSENTIA_UPGRADE_CARD.get();

        // 同设备方块/部件显示名相同（EAE zh_cn/en_us lang 实证），tooltipGroup 传
        // EAE 方块翻译键使「支持设备」列表合并显示，对齐本项目 MGMT-TOOLTIP 范式。
        registerHost(card, "ex_pattern_provider", "block.extendedae.ex_pattern_provider");
        registerHost(card, "ex_pattern_provider_part", "block.extendedae.ex_pattern_provider");
        registerHost(card, "oversize_interface", "block.extendedae.oversize_interface");
        registerHost(card, "oversize_interface_part", "block.extendedae.oversize_interface");
    }

    private static void registerHost(Item card, String path, String tooltipGroup) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);

        Item host = BuiltInRegistries.ITEM.getOptional(id).orElse(null);

        if (host == null || host == Items.AIR) {
            // EAE 版本差异（增删设备/改 ID）时静默跳过该项，不允许启动失败。
            ThaumicEnergistics.LOG
                    .warn("Skipping Extended AE essentia upgrade registration: missing host item {}", id);
            return;
        }

        Upgrades.add(card, host, 1, tooltipGroup);
    }
}
