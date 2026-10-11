package thaumicenergistics.mixins.ae2.provider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

/**
 * T-M1-003 P1：让样板供应器的宿主接口 {@link PatternProviderLogicHost}（Part 与 BlockEntity 两形态共同实现）
 * 具备 {@link IUpgradeableObject} 语义——升级卡读取一律经公开 API
 * {@code ((IUpgradeableObject) host).getUpgrades().isInstalled(卡)}，槽位由谁建的都不影响读取。
 * <p>
 * 蓝本：AppFlux {@code MixinPatternProviderLogicHost}（其自建 {@code IUpgradableMenu} 等价物）。
 * 接口 Mixin 无法持有字段，故 default 方法一律委托给 {@code getLogic()} 侧的实现
 * （真正的清单在 {@link MixinPatternProviderLogic} 里）。
 * <p>
 * <b>属卡槽组</b>（T-M1-015）：本接口仅承载「卡槽读取」语义 → 随卡槽组受 {@code appflux} 互斥；
 * 源质能力另在 {@link MixinPatternProviderLogicEssentia} 恒应用。
 */
@Mixin(value = PatternProviderLogicHost.class, remap = false)
public interface MixinPatternProviderLogicHost extends IUpgradeableObject {

    @Shadow
    PatternProviderLogic getLogic();

    @Override
    default IUpgradeInventory getUpgrades() {
        return ((IUpgradeableObject) this.getLogic()).getUpgrades();
    }
}
