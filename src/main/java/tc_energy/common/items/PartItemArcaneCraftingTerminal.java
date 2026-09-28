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
import tc_energy.common.parts.ArcaneCraftingTerminalPart;

import java.util.List;

public class PartItemArcaneCraftingTerminal extends Item implements IPartItem<ArcaneCraftingTerminalPart> {

    public PartItemArcaneCraftingTerminal(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<ArcaneCraftingTerminalPart> getPartClass() {
        return ArcaneCraftingTerminalPart.class;
    }

    @Override
    public ArcaneCraftingTerminalPart createPart() {
        return new ArcaneCraftingTerminalPart(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tc_energy.arcane_crafting_terminal.desc"));
    }
}
