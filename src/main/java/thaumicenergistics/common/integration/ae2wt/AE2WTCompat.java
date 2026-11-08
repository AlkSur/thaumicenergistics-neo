package thaumicenergistics.common.integration.ae2wt;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.SlotSemantic;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.AE2wtlibAPI;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import de.mari_023.ae2wtlib.api.registration.UpgradeHelper;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 在模组构造阶段登记通用终端的奥术功能，模式、奇点与量子连接沿用 AE2WT。
 * 调用方必须先确认 ae2wtlib 已加载，避免可选 API 被提前解析。
 */
public final class AE2WTCompat {

    /** 通用终端功能名（WTDefinition 键）。 */
    public static final String ARCANE_TERMINAL_NAME = "thaumicenergistics_arcane";

    private AE2WTCompat() {
    }

    /** Preserve the library's mode synchronization and its normal wireless opening checks. */
    public static boolean openArcaneFromInventory(ServerPlayer player,
            ItemMenuHostLocator locator) {
        if (!WTDefinition.exists(ARCANE_TERMINAL_NAME)) return false;
        var definition = WTDefinition.of(ARCANE_TERMINAL_NAME);
        var stack = locator.locateItem(player);
        if (!WUTHandler.hasTerminal(stack, definition)) return false;
        WUTHandler.setCurrentTerminal(player, locator, stack, definition);
        return definition.containerOpener().tryOpen(player, locator, false);
    }

    /** 返回公共 Item 类型，避免普通注册类的字节码验证提前解析 ItemWT 子类。 */
    public static Item createTerminalItem() {
        return new AE2WTArcaneTerminalItem();
    }

    /** 不切换通用终端当前模式，解析其奥术附件供后台充能。 */
    public static WirelessTerminalMenuHost<?> createPassiveArcaneHost(
            ServerPlayer player, ItemMenuHostLocator locator) {
        var stack = locator.locateItem(player);
        if (!(stack.getItem() instanceof ItemWT item)
                || !WTDefinition.exists(ARCANE_TERMINAL_NAME)) return null;
        var definition = WTDefinition.of(ARCANE_TERMINAL_NAME);
        if (item != definition.item() && !stack.has(definition.componentType())) return null;
        return new AE2WTArcaneTerminalMenuHost(item, player, locator, (p, menu) -> { });
    }

    /** 仅在 AE2WT 宿主分支访问官方奇点槽语义，避免缺少可选模组时解析其类型。 */
    public static SlotSemantic singularitySemantic() {
        return AE2wtlibSlotSemantics.SINGULARITY;
    }

    public static ItemStack displayPartner(String partner) {
        if (partner.isEmpty()) return new ItemStack(
                AE2wtlibAPI.getWUT());
        var name = partner;
        var definition = WTDefinition.wirelessTerminals().stream()
                .filter(terminal -> name.equals(terminal.terminalName())).findFirst().orElse(null);
        if (definition == null) return ItemStack.EMPTY;
        return new ItemStack(definition.item());
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
