package thaumicenergistics.mixins.compat.aae;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.menu.AEBaseMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvPatternProviderMenu.class, remap = false)
public abstract class MixinAdvProviderMenu extends AEBaseMenu implements IUpgradeableObject {
    @Shadow @Final protected AdvPatternProviderLogic logic;

    protected MixinAdvProviderMenu(MenuType<?> type, int id, Inventory player, Object host) { super(type, id, player, host); }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V", at = @At("TAIL"))
    private void thE$slots(MenuType<?> type, int id, Inventory player, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        setupUpgrades(((IUpgradeableObject) host).getUpgrades());
    }

    @Override public IUpgradeInventory getUpgrades() { return ((IUpgradeableObject) logic).getUpgrades(); }
}
