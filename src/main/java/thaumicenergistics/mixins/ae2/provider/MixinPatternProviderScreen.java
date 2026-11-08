package thaumicenergistics.mixins.ae2.provider;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为样板供应器界面添加升级面板。
 */
@Mixin(PatternProviderScreen.class)
public abstract class MixinPatternProviderScreen<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void thE$initUpgradePanel(PatternProviderMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style, CallbackInfo ci) {
        this.widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE),
                () -> thE$compatibleUpgrades(menu)));
    }

    /** 空升级槽的提示：兼容升级卡清单（与原版 {@code UpgradeableScreen} 同文案）。 */
    @Unique
    private List<Component> thE$compatibleUpgrades(PatternProviderMenu menu) {
        List<Component> list = new ArrayList<>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(
                ((IUpgradeableObject) menu).getUpgrades().getUpgradableItem()));
        return list;
    }

    public MixinPatternProviderScreen(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
