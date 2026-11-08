package thaumicenergistics.mixins.ae2.provider;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在玩家背包槽之前加入供应器升级槽，保持 AE2 菜单的槽位顺序。
 */
@Mixin(PatternProviderMenu.class)
public abstract class MixinPatternProviderMenu extends AEBaseMenu implements IUpgradeableObject {

    /** 供应器逻辑，声明于目标类自身。 */
    @Final
    @Shadow(remap = false)
    protected PatternProviderLogic logic;

    @Inject(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At(value = "INVOKE", target = "Lappeng/menu/implementations/PatternProviderMenu;createPlayerInventorySlots(Lnet/minecraft/world/entity/player/Inventory;)V"),
            remap = false
    )
    private void thE$setupUpgradeSlots(MenuType<?> menuType, int id, Inventory playerInventory,
            PatternProviderLogicHost host, CallbackInfo ci) {
        this.setupUpgrades(((IUpgradeableObject) host).getUpgrades());
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return ((IUpgradeableObject) this.logic).getUpgrades();
    }

    public MixinPatternProviderMenu(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }
}
