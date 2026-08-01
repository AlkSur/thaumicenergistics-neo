package thaumicenergistics.api;

import net.minecraft.resources.ResourceLocation;
import java.util.OptionalLong;

/** 定义 ThaumicEnergistics 可交互的物品与方块实体。自 1.7.10 移植。 */
public interface IThETransportPermissions {
    /** 注册一个方块实体类，支持抽取与注入。 */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> boolean addTileToBoth(Class<T> tileClass, int capacity);
    /** 注册一个仅支持抽取的方块实体。 */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> boolean addTileToExtract(Class<T> tileClass, int capacity);
    /** 注册一个仅支持注入的方块实体。 */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> boolean addTileToInject(Class<T> tileClass, int capacity);
    /** 获取方块实体类已注册的容量；未注册返回空。 */
    OptionalLong getCapacityForTile(Class<? extends net.minecraft.world.level.block.entity.BlockEntity> tileClass);
    /** 检查该方块实体是否允许抽取。 */
    boolean canExtract(net.minecraft.world.level.block.entity.BlockEntity tile);
    /** 检查该方块实体是否允许注入。 */
    boolean canInject(net.minecraft.world.level.block.entity.BlockEntity tile);
    /** 注册一个带容量的容器物品类型。 */
    void addContainerItem(Class<? extends net.minecraft.world.item.Item> itemClass, int capacity, boolean canHoldPartial);
}
