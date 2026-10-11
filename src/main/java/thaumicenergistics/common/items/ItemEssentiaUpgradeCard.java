package thaumicenergistics.common.items;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.items.materials.UpgradeCardItem;

/**
 * 源质访问卡。
 *
 * <p>本卡沿用 AE2 标准升级卡基类 {@link UpgradeCardItem}。AE2 的工厂函数
 * {@code appeng.api.upgrades.Upgrades.createUpgradeCardItem(Item.Properties)} 的实现即为
 * {@code return new UpgradeCardItem(p);}（AE2 19.2.17 源码 {@code Upgrades.java:134-136}），
 * 继承 {@link UpgradeCardItem} 与走该工厂函数在 AE2 侧行为完全等价，同样满足
 * {@code Upgrades.isUpgradeCardItem(...)} 的升级卡判定，并保留基类固定的「Supported By」提示行机制。
 *
 * <p>差异点：基类的 {@code appendHoverText} 只渲染「Supported By」行，无扩展点；本清单要求额外显示一行
 * 本模组自定义能力描述，故在此覆写 {@code appendHoverText}——先调用父类（保留升级卡判定与 Supported By 机制），
 * 再追加自定义描述行。
 *
 * <p>T-M1-005 范围内**不做** {@code Upgrades.add} 宿主关联；因此「Supported By」行此时为空属预期。
 */
public class ItemEssentiaUpgradeCard extends UpgradeCardItem {

    /** 基础能力描述行（语言文件键）。 */
    private static final String DESC_KEY = "tooltip.thaumicenergistics.essentia_upgrade_card.desc";

    public ItemEssentiaUpgradeCard(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(DESC_KEY));
    }
}
