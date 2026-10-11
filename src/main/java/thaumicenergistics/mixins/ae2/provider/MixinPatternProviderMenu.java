package thaumicenergistics.mixins.ae2.provider;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;

/**
 * T-M1-003 P1：把样板供应器菜单的升级槽接上（槽位 + 卡读取）。
 * <p>
 * 蓝本：AppFlux {@code MixinPatternProviderMenu}（去掉其工具箱部分）。注入时机照抄蓝本——
 * 在 {@code createPlayerInventorySlots} 调用<b>之前</b>执行 {@code setupUpgrades}
 * （{@code AEBaseMenu.setupUpgrades} 为 {@code protected final}，故本 Mixin 须 {@code extends AEBaseMenu}；
 * 置于玩家物品栏之前以保持槽位顺序与原版升级菜单一致）。
 * <p>
 * 注意：注入点处 {@code this.logic} 尚未赋值（它在 {@code createPlayerInventorySlots} 之后才写），
 * 故此处只能用构造器入参 {@code host}。
 */
@Mixin(PatternProviderMenu.class)
public abstract class MixinPatternProviderMenu extends AEBaseMenu implements IUpgradeableObject {

    /** 供应器逻辑，声明于目标类自身（PatternProviderMenu.java:47，protected final）。 */
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
