package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.api.storage.IAspectStack;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

import java.util.List;

public class TileEssentiaProvider extends TileProviderBase implements IEssentiaTransport, EssentiaTransport {

    private static final int TICK_RATE_IDLE = 15;
    private static final int TICK_RATE_URGENT = 5;
    private int tickCount;
    private int tickRate = TICK_RATE_IDLE;

    public TileEssentiaProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_PROVIDER.get(), pos, state);
    }

    @Override
    protected ItemStack getItemFromTile() {
        return new ItemStack(ModItems.ESSENTIA_PROVIDER_ITEM.get());
    }

    @Override
    protected double getIdlePowerUsage() {
        // 基础 3 + 每连接 1 个接收器 +5
        return 3.0 + 5.0 * getLinkedReceiverCount();
    }

    // ===== 无线接收器连接管理 =====
    /** 最多连接的接收器数量。 */
    public static final int MAX_LINKED_RECEIVERS = 8;
    /** 最大连接距离（格）。 */
    public static final int MAX_LINK_DISTANCE = 32;

    private List<BlockPos> linkedReceivers = new java.util.ArrayList<>();

    public java.util.List<BlockPos> getLinkedReceivers() {
        return linkedReceivers;
    }

    public int getLinkedReceiverCount() {
        return linkedReceivers.size();
    }

    /** 注册一个接收器连接（校验数量与距离，失败返回原因文案；成功返回 null）。 */
    public String addLinkedReceiver(BlockPos receiverPos) {
        if (linkedReceivers.contains(receiverPos)) return null; // 已连接
        if (linkedReceivers.size() >= MAX_LINKED_RECEIVERS) {
            return "§c该源质供应器已连接满 " + MAX_LINKED_RECEIVERS + " 个接收器";
        }
        double dist = Math.sqrt(worldPosition.distSqr(receiverPos));
        if (dist > MAX_LINK_DISTANCE) {
            return "§c距离过远（" + (int) Math.ceil(dist) + " 格 > " + MAX_LINK_DISTANCE + " 格上限）";
        }
        linkedReceivers.add(receiverPos);
        setChanged();
        updatePower();
        return null;
    }

    public void removeLinkedReceiver(BlockPos receiverPos) {
        if (linkedReceivers.remove(receiverPos)) {
            setChanged();
            updatePower();
        }
    }

    /** 周期校验：清除已失效的接收器连接（方块被拆除）。 */
    public void validateLinkedReceivers() {
        if (level == null || linkedReceivers.isEmpty()) return;
        linkedReceivers.removeIf(pos -> {
            boolean gone = !(level.getBlockEntity(pos) instanceof TileEssentiaProviderConnection);
            if (gone) setChanged();
            return gone;
        });
        updatePower();
    }

    private void updatePower() {
        if (mainNode != null) {
            mainNode.setIdlePowerUsage(getIdlePowerUsage());
        }
    }

    /** getEssentiaList 短时缓存：TC 管道 suction 每 tick 调 getEssentiaType，避免每 tick 全量遍历。 */
    private List<IAspectStack> cachedEssentiaList = java.util.List.of();
    private int essentiaListCacheTimer = 0;
    private static final int ESSENTIA_LIST_CACHE_TICKS = 20;

    private List<thaumicenergistics.api.storage.IAspectStack> getCachedEssentiaList() {
        if (essentiaListCacheTimer <= 0) {
            essentiaListCacheTimer = ESSENTIA_LIST_CACHE_TICKS;
            cachedEssentiaList = essentiaGrid != null ? essentiaGrid.getEssentiaList() : java.util.List.of();
        }
        return cachedEssentiaList;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (!mainNode.isActive()) return;

        if (essentiaListCacheTimer > 0) essentiaListCacheTimer--;

        // 周期校验无线接收器连接（约每 2 秒）
        if (++validateCounter >= 40) {
            validateCounter = 0;
            validateLinkedReceivers();
        }

        if (++tickCount >= tickRate) {
            tickCount = 0;
            tickRate = TICK_RATE_IDLE;
            takeEssentiaFromTransportNeighbors();
        }
    }

    private int validateCounter = 0;

    // ===== IEssentiaTransport =====

    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return true;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {
    }

    @Override
    public Aspect getSuctionType(Direction side) {
        return null; // 接受所有类型
    }

    @Override
    public int getSuctionAmount(Direction side) {
        return 8; // 最大吸力
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) {
        if (!mainNode.isActive()) return 0;
        long extracted = extractEssentiaFromNetwork(aspect, amount, false);
        if (extracted > 0) {
            tickRate = TICK_RATE_URGENT;
        }
        return (int) extracted;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        if (!mainNode.isActive()) return 0;
        long rejected = injectEssentiaToNetwork(aspect, amount, false);
        long accepted = amount - rejected;
        if (accepted > 0) {
            tickRate = TICK_RATE_URGENT;
        }
        return (int) accepted;
    }

    @Override
    public Aspect getEssentiaType(Direction side) {
        Aspect wanted = getNeighborWantedAspect(side);
        if (wanted != null && getAspectAmountInNetwork(wanted) > 0) {
            return wanted;
        }
        // 邻居无特定需求时，返回网络中第一种有存量的源质（列表短时缓存，避免 TC 管道每 tick suction 全量遍历）
        if (essentiaGrid != null) {
            for (var stack : getCachedEssentiaList()) {
                if (stack.amount() > 0) {
                    return Aspect.getAspect(stack.aspectId().getPath());
                }
            }
        }
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        Aspect wanted = getNeighborWantedAspect(side);
        if (wanted != null) {
            return (int) Math.min(getAspectAmountInNetwork(wanted), Integer.MAX_VALUE);
        }
        return 0;
    }

    @Override
    public int getMinimumSuction() {
        return 1;
    }

    // ===== 供源质无线接收器转发的公开方法 =====

    public boolean thE_isActive() {
        return mainNode.isActive();
    }

    public long thE_queryAmount(Aspect aspect) {
        return getAspectAmountInNetwork(aspect);
    }

    public long thE_extract(Aspect aspect, long amount, boolean simulate) {
        return extractEssentiaFromNetwork(aspect, amount, simulate);
    }

    public long thE_inject(Aspect aspect, long amount, boolean simulate) {
        return injectEssentiaToNetwork(aspect, amount, simulate);
    }

    /** 网络中第一种有存量的源质（供接收器无特定需求时兜底）。 */
    public Aspect thE_firstAvailable() {
        if (essentiaGrid != null) {
            for (var stack : getCachedEssentiaList()) {
                if (stack.amount() > 0) {
                    return Aspect.getAspect(stack.aspectId().getPath());
                }
            }
        }
        return null;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    // ===== 内部 =====

    private Aspect getNeighborWantedAspect(Direction side) {
        BlockPos neighborPos = worldPosition.relative(side);
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        if (neighbor instanceof IEssentiaTransport transport) {
            return transport.getSuctionType(side.getOpposite());
        }
        return null;
    }

    private void takeEssentiaFromTransportNeighbors() {
        for (Direction side : Direction.values()) {
            if (!canInputFrom(side)) continue;

            BlockPos sourcePos = worldPosition.relative(side);
            BlockEntity sourceTile = level.getBlockEntity(sourcePos);
            if (!(sourceTile instanceof IEssentiaTransport source)) continue;

            Direction sourceSide = side.getOpposite();
            if (!source.canOutputTo(sourceSide)) continue;
            if (source.getEssentiaAmount(sourceSide) <= 0) continue;

            int dSuck = getSuctionAmount(side);
            if (dSuck < source.getMinimumSuction() || dSuck < source.getSuctionAmount(sourceSide)) {
                continue;
            }

            Aspect sourceAspect = source.getEssentiaType(sourceSide);
            if (sourceAspect == null) continue;

            // 模拟注入
            long simulated = injectEssentiaToNetwork(sourceAspect, 1, true);
            if (simulated == 0) {
                // 实际提取并注入
                int taken = source.takeEssentia(sourceAspect, 1, sourceSide);
                if (taken > 0) {
                    injectEssentiaToNetwork(sourceAspect, taken, false);
                    tickRate = TICK_RATE_URGENT;
                }
            }
        }
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (BlockPos pos : linkedReceivers) {
            list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        tag.put("LinkedReceivers", list);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedReceivers.clear();
        if (tag.contains("LinkedReceivers")) {
            for (net.minecraft.nbt.Tag t : tag.getList("LinkedReceivers", net.minecraft.nbt.Tag.TAG_LONG)) {
                linkedReceivers.add(BlockPos.of(((net.minecraft.nbt.LongTag) t).getAsLong()));
            }
        }
    }
}