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
import thaumicenergistics.common.parts.EssentiaStorageBusPart;

import java.util.List;

public class PartItemEssentiaStorageBus extends Item implements IPartItem<EssentiaStorageBusPart> {

    public static PartItemEssentiaStorageBus create() {
        return new PartItemEssentiaStorageBus(new Item.Properties().stacksTo(64));
    }

    public PartItemEssentiaStorageBus(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<EssentiaStorageBusPart> getPartClass() {
        return EssentiaStorageBusPart.class;
    }

    // 传 this：本类实现了 IPartItem<EssentiaStorageBusPart>
    @Override
    public EssentiaStorageBusPart createPart() {
        return new EssentiaStorageBusPart(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.essentia_storage_bus.desc"));
    }
}