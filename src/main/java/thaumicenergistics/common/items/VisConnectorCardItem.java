package thaumicenergistics.common.items;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.items.materials.UpgradeCardItem;

/**
 * vis 连接卡（T-M3-VISCARD-REV：升级卡化）——插入无线奥术合成终端的升级槽后，
 * 终端自动连接所在 ME 网络的要素中继接口为菜单内法杖补 vis。
 * 沿用 AE2 升级卡基类（Supported By 机制白送），升级槽白名单见主类 Upgrades.add。
 */
public class VisConnectorCardItem extends UpgradeCardItem {

    private static final String DESC_KEY = "tooltip.thaumicenergistics.vis_connector_card.desc";

    public VisConnectorCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(DESC_KEY));
    }
}
