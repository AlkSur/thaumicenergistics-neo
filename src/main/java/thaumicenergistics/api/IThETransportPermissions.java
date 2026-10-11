package thaumicenergistics.api;

import java.util.OptionalLong;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 定义 ThaumicEnergistics 可交互的物品与方块实体。自 1.7.10 移植。 */
public interface IThETransportPermissions {
    /** 注册一个方块实体类，支持抽取与注入。 */
    <T extends BlockEntity> boolean addTileToBoth(Class<T> tileClass, int capacity);
    /** 注册一个仅支持抽取的方块实体。 */
    <T extends BlockEntity> boolean addTileToExtract(Class<T> tileClass, int capacity);
    /** 注册一个仅支持注入的方块实体。 */
    <T extends BlockEntity> boolean addTileToInject(Class<T> tileClass, int capacity);
    /** 获取方块实体类已注册的容量；未注册返回空。 */
    OptionalLong getCapacityForTile(Class<? extends BlockEntity> tileClass);
    /** 检查该方块实体是否允许抽取。 */
    boolean canExtract(BlockEntity tile);
    /** 检查该方块实体是否允许注入。 */
    boolean canInject(BlockEntity tile);
    /** 注册一个带容量的容器物品类型。 */
    void addContainerItem(Class<? extends Item> itemClass, int capacity, boolean canHoldPartial);
}
