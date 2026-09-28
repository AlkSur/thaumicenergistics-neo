package tc_energy.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import tc_energy.api.storage.IAspectStack;
import tc_energy.init.ModBlockEntities;
import tc_energy.init.ModItems;

import java.util.List;

/**
 * 源质供应器（AE2 网络对 TC 世界暴露的传输端点）。
 * 实现 Ren 版 {@link EssentiaTransport}：TC 管道/机器通过 canOutputTo/takeEssentia 抽取，
 * 通过 canInputFrom/addEssentia 注入；自身周期从相邻管道端点拉取源质进网络。
 */
public class TileEssentiaProvider extends TileProviderBase implements EssentiaTransport {

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

    /** getEssentiaList 短时缓存：TC 管道 suction 每 tick 调 essentiaType，避免每 tick 全量遍历。 */
    private List<IAspectStack> cachedEssentiaList = java.util.List.of();
    private int essentiaListCacheTimer = 0;
    private static final int ESSENTIA_LIST_CACHE_TICKS = 20;

    private List<tc_energy.api.storage.IAspectStack> getCachedEssentiaList() {
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

    // ===== Ren 版 EssentiaTransport =====

    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return true;
    }

    @Override
    public dev.tc4port.thaumcraft.api.aspect.AspectId suctionType(Direction face) {
        return null; // 接受所有类型
    }

    @Override
    public int suctionAmount(Direction face) {
        return 8; // 最大吸力
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!mainNode.isActive() || aspect == null || amount <= 0) return 0;
        if (mode == EssentiaTransferMode.SIMULATE) {
            return (int) Math.min(getAspectAmountInNetwork(aspect), amount);
        }
        long extracted = extractEssentiaFromNetwork(aspect, amount, false);
        if (extracted > 0) {
            tickRate = TICK_RATE_URGENT;
        }
        return (int) extracted;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!mainNode.isActive() || aspect == null || amount <= 0) return 0;
        if (mode == EssentiaTransferMode.SIMULATE) {
            // 乐观接受；实际注入按网络返回值结算（调用方侧有兜底）
            return amount;
        }
        long rejected = injectEssentiaToNetwork(aspect, amount, false);
        long accepted = amount - rejected;
        if (accepted > 0) {
            tickRate = TICK_RATE_URGENT;
        }
        return (int) accepted;
    }

    @Override
    public AspectId essentiaType(Direction face) {
        AspectId wanted = getNeighborWantedAspect(face);
        if (wanted != null && getAspectAmountInNetwork(wanted) > 0) {
            return wanted;
        }
        // 邻居无特定需求时，返回网络中第一种有存量的源质（短时缓存，避免每 tick suction 全量遍历）
        if (essentiaGrid != null) {
            for (var stack : getCachedEssentiaList()) {
                if (stack.amount() > 0) {
                    return AspectId.of(stack.aspectId());
                }
            }
        }
        return null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        AspectId wanted = getNeighborWantedAspect(face);
        if (wanted != null) {
            return (int) Math.min(getAspectAmountInNetwork(wanted), Integer.MAX_VALUE);
        }
        return 0;
    }

    @Override
    public int minimumSuction() {
        return 1;
    }

    // ===== 供源质无线接收器转发的公开方法 =====

    public boolean thE_isActive() {
        return mainNode.isActive();
    }

    public long thE_queryAmount(AspectId aspect) {
        return getAspectAmountInNetwork(aspect);
    }

    public long thE_extract(AspectId aspect, long amount, boolean simulate) {
        return extractEssentiaFromNetwork(aspect, amount, simulate);
    }

    public long thE_inject(AspectId aspect, long amount, boolean simulate) {
        return injectEssentiaToNetwork(aspect, amount, simulate);
    }

    /** 网络中第一种有存量的源质（供接收器无特定需求时兜底）。 */
    public AspectId thE_firstAvailable() {
        if (essentiaGrid != null) {
            for (var stack : getCachedEssentiaList()) {
                if (stack.amount() > 0) {
                    return AspectId.of(stack.aspectId());
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

    private AspectId getNeighborWantedAspect(Direction side) {
        BlockPos neighborPos = worldPosition.relative(side);
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        if (neighbor instanceof EssentiaTransport transport) {
            return transport.suctionType(side.getOpposite());
        }
        return null;
    }

    private void takeEssentiaFromTransportNeighbors() {
        for (Direction side : Direction.values()) {
            BlockPos sourcePos = worldPosition.relative(side);
            BlockEntity sourceTile = level.getBlockEntity(sourcePos);
            if (!(sourceTile instanceof EssentiaTransport source)) continue;

            Direction sourceSide = side.getOpposite();
            if (!source.canOutputTo(sourceSide)) continue;
            if (source.essentiaAmount(sourceSide) <= 0) continue;

            AspectId sourceAspect = source.essentiaType(sourceSide);
            if (sourceAspect == null) continue;

            // 模拟注入：网络能装下才真正抽取（避免抽出来丢源质）
            long simulated = injectEssentiaToNetwork(sourceAspect, 1, true);
            if (simulated == 0) {
                int taken = source.takeEssentia(sourceAspect, 1, sourceSide, EssentiaTransferMode.EXECUTE);
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
