package tc_energy.common.container;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;

/**
 * 替代旧 {@code MixinMEStorageMenuAccessors} + {@code ThEEssentiaMenuAccess} 的公开访问器。
 * <p>
 * AE2 1.21.1 的 {@link MEStorageMenu#getHost()} 是公开 API，普通 ME 终端宿主（终端部件、
 * 无线终端等）通过 {@code ITerminalHost.getInventory()} 暴露 ME 存储；能量源则优先取宿主自身的
 * {@link IEnergySource} 实现，否则落到所在网格的 {@code IEnergyService}。
 */
public final class MenuAccess {

    private MenuAccess() {}

    /** ME 存储（替代旧 mixin 暴露的 protected storage 字段）。 */
    public static MEStorage getStorage(MEStorageMenu menu) {
        return menu.getHost().getInventory();
    }

    /** 能量源（替代旧 mixin 暴露的 protected energySource 字段）。 */
    public static IEnergySource getEnergySource(MEStorageMenu menu) {
        var host = menu.getHost();
        if (host instanceof IEnergySource src) {
            return src;
        }
        if (host instanceof IActionHost actionHost) {
            var node = actionHost.getActionableNode();
            if (node != null && node.isActive() && node.getGrid() != null) {
                return node.getGrid().getEnergyService();
            }
        }
        return IEnergySource.empty();
    }
}
