package thaumicenergistics.common.storage;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import thaumicenergistics.api.IThETransportPermissions;
import java.util.*;

public class TransportPermissions implements IThETransportPermissions {
    private final Map<Class<? extends BlockEntity>, Integer> tileExtract = new HashMap<>();
    private final Map<Class<? extends BlockEntity>, Integer> tileInject = new HashMap<>();
    private final Map<Class<? extends Item>, ContainerItemInfo> itemPerms = new HashMap<>();

    @Override
    public <T extends BlockEntity> boolean addTileToBoth(Class<T> c, int cap) {
        boolean a = addTileToExtract(c, cap); boolean b = addTileToInject(c, cap); return a || b;
    }
    @Override
    public <T extends BlockEntity> boolean addTileToExtract(Class<T> c, int cap) { return tileExtract.putIfAbsent(c, cap) == null; }
    @Override
    public <T extends BlockEntity> boolean addTileToInject(Class<T> c, int cap) { return tileInject.putIfAbsent(c, cap) == null; }
    @Override
    public java.util.OptionalLong getCapacityForTile(Class<? extends BlockEntity> c) {
        Integer v = tileExtract.get(c); return v != null ? java.util.OptionalLong.of(v) : java.util.OptionalLong.empty();
    }
    @Override
    public boolean canExtract(BlockEntity tile) { return tile != null && tileExtract.containsKey(tile.getClass()); }
    @Override
    public boolean canInject(BlockEntity tile) { return tile != null && tileInject.containsKey(tile.getClass()); }
    @Override
    public void addContainerItem(Class<? extends Item> c, int cap, boolean partial) { itemPerms.put(c, new ContainerItemInfo(cap, partial)); }

    public record ContainerItemInfo(int capacity, boolean canHoldPartial) {}
}
