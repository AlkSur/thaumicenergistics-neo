package thaumicenergistics.mixins.compat.aae;

import java.util.ArrayList;
import java.util.List;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.SmallAdvPatternProviderScreen;
import net.pedroksl.advanced_ae.gui.advpatternprovider.SmallAdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmallAdvPatternProviderScreen.class, remap = false)
public abstract class MixinSmallAdvProviderScreen extends AEBaseScreen<SmallAdvPatternProviderMenu> {
    protected MixinSmallAdvProviderScreen(SmallAdvPatternProviderMenu menu, Inventory player, Component title, ScreenStyle style) {
        super(menu, player, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thE$panel(SmallAdvPatternProviderMenu menu, Inventory player, Component title, ScreenStyle style, CallbackInfo ci) {
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), () -> {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(GuiText.CompatibleUpgrades.text());
            tooltip.addAll(Upgrades.getTooltipLinesForMachine(((IUpgradeableObject) menu).getUpgrades().getUpgradableItem()));
            return tooltip;
        }));
    }
}
