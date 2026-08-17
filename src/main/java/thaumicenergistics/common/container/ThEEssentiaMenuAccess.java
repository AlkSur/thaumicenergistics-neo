package thaumicenergistics.common.container;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.MEStorage;

/**
 * 由 {@code MixinMEStorageMenuAccessors} 注入到 {@code MEStorageMenu} 的访问器接口。
 * <p>
 * 编译期接口存在、运行期目标类实现，调用方（如网络包处理）通过
 * {@code instanceof ThEEssentiaMenuAccess} 安全访问 ME 存储与能量源。
 * <p>
 * 注意：必须放在非 mixin 包（mixin 包 {@code thaumicenergistics.mixins.*} 的类
 * 禁止被普通类直接引用）。
 */
public interface ThEEssentiaMenuAccess {

    /** ME 存储（对应 MEStorageMenu.storage）。 */
    MEStorage thE_getStorage();

    /** 能量源（对应 MEStorageMenu.energySource）。 */
    IEnergySource thE_getEnergySource();
}
