package tc_energy.common.items;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;
import tc_energy.common.parts.EssentiaExportBusPart;

import java.util.List;

public class PartItemEssentiaExportBus extends Item implements IPartItem<EssentiaExportBusPart> {

    public static PartItemEssentiaExportBus create() {
        return new PartItemEssentiaExportBus(new Item.Properties().stacksTo(64));
    }

    public PartItemEssentiaExportBus(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<EssentiaExportBusPart> getPartClass() {
        return EssentiaExportBusPart.class;
    }

    // 传 this：本类实现了 IPartItem<EssentiaExportBusPart>
    @Override
    public EssentiaExportBusPart createPart() {
        return new EssentiaExportBusPart(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tc_energy.essentia_export_bus.desc"));
        tooltip.add(Component.translatable("tooltip.tc_energy.essentia_export_bus.hint"));
    }
}