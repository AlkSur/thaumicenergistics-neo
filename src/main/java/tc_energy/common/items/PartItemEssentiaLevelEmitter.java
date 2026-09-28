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
import tc_energy.common.parts.EssentiaLevelEmitterPart;

import java.util.List;

public class PartItemEssentiaLevelEmitter extends Item implements IPartItem<EssentiaLevelEmitterPart> {

    public PartItemEssentiaLevelEmitter(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<EssentiaLevelEmitterPart> getPartClass() {
        return EssentiaLevelEmitterPart.class;
    }

    @Override
    public EssentiaLevelEmitterPart createPart() {
        return new EssentiaLevelEmitterPart(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tc_energy.essentia_level_emitter.desc"));
    }
}
