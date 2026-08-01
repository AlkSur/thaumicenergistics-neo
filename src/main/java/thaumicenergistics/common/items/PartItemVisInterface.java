package thaumicenergistics.common.items;

import appeng.items.parts.PartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.parts.VisInterfacePart;

import java.util.List;

public class PartItemVisInterface extends PartItem<VisInterfacePart> {

    public PartItemVisInterface(Properties properties) {
        super(properties, VisInterfacePart.class, VisInterfacePart::new);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.vis_interface.desc"));
    }
}
