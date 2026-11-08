package thaumicenergistics.common.items;

import appeng.core.localization.GuiText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumcraft.common.blockentities.WandRechargePedestalBlockEntity;
import thaumicenergistics.ThaumicEnergistics;

@EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class CrystalToolEvents {
    private CrystalToolEvents() {}

    @SubscribeEvent
    public static void placeOnPedestal(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getItemStack().getItem() instanceof ThaumicCrystalTools.Repairable)) return;
        var player = event.getEntity();
        if (player.isSpectator() || !player.mayBuild()
                || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof WandRechargePedestalBlockEntity pedestal)
                || !pedestal.heldStack().isEmpty()) return;
        if (!event.getLevel().isClientSide()) {
            pedestal.setItem(0, event.getItemStack().copyWithCount(1));
            if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof ThaumicCrystalTools.Repairable)) return;
        if (event.getItemStack().getItem() instanceof ThaumicCrystalTools.Intrinsic tool && event.getEntity() != null) {
            event.getEntity().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(tool.intrinsic())
                    .ifPresent(holder -> event.getToolTip().add(GuiText.IntrinsicEnchant
                            .text(Enchantment.getFullname(holder, 1))));
        }
        event.getToolTip().add(Component.translatable("thaumicenergistics.tools.vis_repair").withStyle(ChatFormatting.GRAY));
    }
}
