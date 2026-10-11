package thaumicenergistics.mixins.ae2.provider;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;

/**
 * T-M1-003 P1：给样板供应器界面挂上升级槽面板（{@link UpgradesPanel}）。
 * <p>
 * 蓝本：AppFlux {@code MixinPatternProviderScreen}。其「手动补写 style（TOOLBOX 槽位 + 贴图 + WidgetStyle）」
 * 段<b>仅为 AppFlux 自己的工具箱面板</b>服务——升级面板无需补写：AE2 原生
 * {@code assets/ae2/screens/pattern_provider.json} 经 {@code includes} 引入 {@code common/common.json}，
 * 后者已内置 {@code "upgrades"} widget 条目（{@code right:2, top:0}）。
 * 故本类<b>丢弃</b>整段 style 补写与工具箱面板（AppFlux 私有件），只保留升级面板挂载。
 * <p>
 * 槽位来自 {@code PatternProviderMenu} 的 {@code SlotSemantics.UPGRADE}（由
 * {@link MixinPatternProviderMenu} 经 {@code setupUpgrades} 注入）。
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
