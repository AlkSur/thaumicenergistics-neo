package thaumicenergistics.common.integration.ae2wt;

import net.neoforged.fml.ModList;

import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import de.mari_023.ae2wtlib.api.registration.UpgradeHelper;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;

/**
 * AE2WT（无线终端库）可选兼容注册：把无线奥术终端注册为通用终端的奥术功能
 * （功能名 {@code thaumicenergistics_arcane}），合并进 ae2wtlib 无线通用终端后
 * 可切换奥术模式；模式切换、奇点库存与量子连接由 AE2WT 体系自理。
 * <p>
 * 注册时机为模组构造阶段——{@code AddTerminalEvent.register} 登记的一次性回调
 * 在 RegisterEvent(ITEM) 由 API 的入口监听器统一执行（随后 {@code UpgradeHelper.addUpgrades}
 * 应用全终端升级卡准入），构造期登记必然早于执行。完整 ae2wtlib 缺席时本类
 * 不被加载（唯一调用点在主类构造器 ModList 守护之后），不产生缺类问题。
 */
public final class AE2WTCompat {

    /** 通用终端功能名（WTDefinition 键）。 */
    public static final String ARCANE_TERMINAL_NAME = "thaumicenergistics_arcane";

    private AE2WTCompat() {
    }

    /** 返回公共 Item 类型，避免普通注册类的字节码验证提前解析 ItemWT 子类。 */
    public static net.minecraft.world.item.Item createTerminalItem() {
        return new AE2WTArcaneTerminalItem();
    }

    /** 不切换通用终端当前模式，解析其奥术附件供后台充能。 */
    public static appeng.helpers.WirelessTerminalMenuHost<?> createPassiveArcaneHost(
            net.minecraft.server.level.ServerPlayer player, appeng.menu.locator.ItemMenuHostLocator locator) {
        var stack = locator.locateItem(player);
        if (!(stack.getItem() instanceof ItemWT item)
                || !de.mari_023.ae2wtlib.api.registration.WTDefinition.exists(ARCANE_TERMINAL_NAME)) return null;
        var definition = de.mari_023.ae2wtlib.api.registration.WTDefinition.of(ARCANE_TERMINAL_NAME);
        if (item != definition.item() && !stack.has(definition.componentType())) return null;
        return new AE2WTArcaneTerminalMenuHost(item, player, locator, (p, menu) -> { });
    }

    /**
     * 返回官方奇点槽语义（{@code AE2wtlibSlotSemantics.SINGULARITY}）。菜单侧经本方法
     * 隔离解析，防止 ae2wtlib 缺席时普通加载路径触发缺类；调用点均被宿主分支守护
     * （仅 AE2WT 宿主可达，分支条件成立即证明 ae2wtlib 在场）。
     */
    public static appeng.menu.SlotSemantic singularitySemantic() {
        return de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics.SINGULARITY;
    }

    public static net.minecraft.world.item.ItemStack displayPartner(String partner) {
        var name = partner.isEmpty() ? "crafting" : partner;
        var definition = de.mari_023.ae2wtlib.api.registration.WTDefinition.wirelessTerminals().stream()
                .filter(terminal -> name.equals(terminal.terminalName())).findFirst().orElse(null);
        if (definition == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return partner.isEmpty() ? definition.universalTerminal().copy()
                : new net.minecraft.world.item.ItemStack(definition.item());
    }

    public static void registerArcaneTerminal() {
        AddTerminalEvent.register(event -> {
            var item = ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
            if (!(item instanceof ItemWT arcaneItem)) {
                // ae2wtlib 存在但注册工厂走了独立实现（版本守卫分支），不注册避免半残功能。
                ThaumicEnergistics.LOG.warn(
                        "Skipping AE2WT arcane terminal registration: item is not an ItemWT implementation");
                return;
            }
            event.builder(ARCANE_TERMINAL_NAME,
                    AE2WTArcaneTerminalMenuHost::new,
                    ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE,
                    arcaneItem,
                    Icon.CRAFTING)
                    .addTerminal();
            // vis 连接卡准入全部通用终端功能（对齐 AE2WT 桥卡先例）：奥术模式生效，
            // 其余模式装卡无作用但可随库存保留（卡片提示由 AE2WT 全终端机制统一处理）。
            UpgradeHelper.addUpgradeToAllTerminals(ModItems.VIS_CONNECTOR_CARD.get(), 1);
        });
    }
}
