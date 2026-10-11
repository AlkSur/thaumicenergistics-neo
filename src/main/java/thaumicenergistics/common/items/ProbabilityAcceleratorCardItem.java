package thaumicenergistics.common.items;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.items.materials.UpgradeCardItem;

/**
 * 概率加速卡（M6）：手持右键概率之脑直接插入（上限 4 张），缩短产出周期。
 * 沿用 AE2 升级卡基类（Supported By 机制白送），宿主白名单见主类 Upgrades.add。
 */
public class ProbabilityAcceleratorCardItem extends UpgradeCardItem {

    private static final String DESC_KEY = "tooltip.thaumicenergistics.probability_accelerator_card.desc";

    public ProbabilityAcceleratorCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(DESC_KEY));
    }
}
