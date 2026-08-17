package thaumicenergistics.mixins.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;

import thaumicenergistics.common.container.ThEEssentiaMenuAccess;

/**
 * 为 {@link MEStorageMenu} 实现 {@link ThEEssentiaMenuAccess} 接口，
 * 暴露其 protected 的 storage / energySource 供 ThaumicEnergistics 的
 * {@code EssentiaFillHelper} 访问。
 * <p>
 * 通过 mixin 向目标类追加方法（mixin 内普通方法会被注入到目标类），
 * 方法名前缀 thE_ 避免与 AE2 未来版本可能新增的方法冲突。
 */
@Mixin(targets = "appeng.menu.me.common.MEStorageMenu", remap = false)
public abstract class MixinMEStorageMenuAccessors implements ThEEssentiaMenuAccess {

    @Shadow(remap = false)
    protected MEStorage storage;

    @Shadow(remap = false)
    protected IEnergySource energySource;

    @Override
    public MEStorage thE_getStorage() {
        return this.storage;
    }

    @Override
    public IEnergySource thE_getEnergySource() {
        return this.energySource;
    }
}
