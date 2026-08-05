package thaumicenergistics.common.items;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.parts.EssentiaImportBusPart;

import java.util.List;

public class PartItemEssentiaImportBus extends Item implements IPartItem<EssentiaImportBusPart> {

    public static PartItemEssentiaImportBus create() {
        return new PartItemEssentiaImportBus(new Item.Properties().stacksTo(64));
    }

    public PartItemEssentiaImportBus(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<EssentiaImportBusPart> getPartClass() {
        return EssentiaImportBusPart.class;
    }

    // 构造器要求 IPartItem<?> partItem，传 this 即可
    @Override
    public EssentiaImportBusPart createPart() {
        return new EssentiaImportBusPart(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.essentia_import_bus.desc"));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.essentia_import_bus.hint"));
    }
}