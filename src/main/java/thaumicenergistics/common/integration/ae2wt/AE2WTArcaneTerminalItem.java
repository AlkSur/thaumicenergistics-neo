package thaumicenergistics.common.integration.ae2wt;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.locator.ItemMenuHostLocator;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

import thaumicenergistics.init.ModMenuTypes;

/**
 * AE2WT 体系下的无线奥术终端物品：继承 {@code ItemWT} 纳入通用终端合并/模式切换
 * 体系（{@code WTDefinition} 类型硬绑定 ItemWT），奥术模式打开本模组同一奥术菜单。
 * <p>
 * 仅 ae2wtlib 存在时由注册工厂实例化（注册 ID 不变），能量容量与独立实现一致——
 * {@code ItemWT} 无参构造内部即 AEConfig 无线终端电池 + stacksTo(1)。宿主创建由
 * {@code ItemWT.getMenuHost} 按 WTDefinition 的工厂自动分派，无需覆写。
 */
public class AE2WTArcaneTerminalItem extends ItemWT {

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE;
    }
}
