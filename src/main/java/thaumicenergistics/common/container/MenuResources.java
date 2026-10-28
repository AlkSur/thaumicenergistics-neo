package thaumicenergistics.common.container;

import java.util.Objects;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;

/**
 * 从 {@link MEStorageMenu} 宿主的公开接口取得存储与能量源，
 * 替代原 MixinMEStorageMenuAccessors 注入的私有字段访问器。
 * 取得规则与 AE2 菜单构造器一致：存储=宿主 getInventory()（要求非空）；
 * 能量源=宿主实现 IEnergySource 时优先用宿主（无线终端电池扣费语义），
 * 否则宿主实现 IActionHost 时动态代理——每次扣费重新查询节点与网格，
 * 不捕获首次结果，保证拆线/重连/网络合并后不使用旧网格；均不成立时用空源。
 * 不保存世界数据，不引入数据组件，不建立菜单缓存。
 */
public final class MenuResources {
    private MenuResources() {
    }

    public static MEStorage getStorage(MEStorageMenu menu) {
        Objects.requireNonNull(menu, "menu");
        return Objects.requireNonNull(
                menu.getHost().getInventory(), "host inventory is null");
    }

    public static IEnergySource getEnergySource(MEStorageMenu menu) {
        Objects.requireNonNull(menu, "menu");
        var host = menu.getHost();

        if (host instanceof IEnergySource energySource) {
            return energySource;
        }

        if (host instanceof IActionHost actionHost) {
            return (amount, mode, multiplier) -> {
                var node = actionHost.getActionableNode();
                if (node == null || !node.isActive()) {
                    return 0.0;
                }
                var grid = node.getGrid();
                if (grid == null) {
                    return 0.0;
                }
                return grid.getEnergyService().extractAEPower(
                        amount, mode, multiplier);
            };
        }

        return IEnergySource.empty();
    }
}
