package thaumicenergistics.mixins.compat.aae;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = AdvPatternProviderLogicHost.class, remap = false)
public interface MixinAdvProviderHost extends IUpgradeableObject {
    @Override
    default IUpgradeInventory getUpgrades() {
        return ((IUpgradeableObject) ((AdvPatternProviderLogicHost) this).getLogic()).getUpgrades();
    }
}
