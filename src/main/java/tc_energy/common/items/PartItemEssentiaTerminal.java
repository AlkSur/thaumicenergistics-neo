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
import tc_energy.common.parts.EssentiaTerminalPart;

import java.util.List;

public class PartItemEssentiaTerminal extends Item implements IPartItem<EssentiaTerminalPart> {

    public PartItemEssentiaTerminal(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<EssentiaTerminalPart> getPartClass() {
        return EssentiaTerminalPart.class;
    }

    @Override
    public EssentiaTerminalPart createPart() {
        return new EssentiaTerminalPart(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tc_energy.essentia_terminal.desc"));
    }
}