package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.ThaumicEnergistics;
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
        return 3.0;
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

        if (++tickCount >= tickRate) {
            tickCount = 0;
            tickRate = TICK_RATE_IDLE;
            takeEssentiaFromTransportNeighbors();
        }
    }

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
        ThaumicEnergistics.LOG.debug("[MyHano-E] takeEssentia: aspect={}, amount={}, side={}, active={}",
                aspect != null ? aspect.getTag() : "null", amount, side, mainNode.isActive());
        if (!mainNode.isActive()) return 0;
        long extracted = extractEssentiaFromNetwork(aspect, amount, false);
        ThaumicEnergistics.LOG.debug("[MyHano-E] takeEssentia result={}", extracted);
        if (extracted > 0) {
            tickRate = TICK_RATE_URGENT;
        }
        return (int) extracted;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        ThaumicEnergistics.LOG.debug("[MyHano-E] addEssentia: aspect={}, amount={}, side={}, active={}",
                aspect != null ? aspect.getTag() : "null", amount, side, mainNode.isActive());
        if (!mainNode.isActive()) return 0;
        long rejected = injectEssentiaToNetwork(aspect, amount, false);
        long accepted = amount - rejected;
        if (accepted > 0) {
            ThaumicEnergistics.LOG.debug("[MyHano-E] addEssentia accepted={}", accepted);
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
}