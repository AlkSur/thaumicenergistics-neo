package tc_energy.common.tiles;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import tc_energy.init.ModBlockEntities;
import tc_energy.init.ModBlocks;

/**
 * 源质无线接收器：通过无线连接器与源质供应器建立连接后，
 * 作为纯中继（无 AE 节点），把 TC 管道/炼金塔的取源质请求转发到连接的供应器。
 * 实现 Ren 版 {@link EssentiaTransport}。
 */
public class TileEssentiaProviderConnection extends BlockEntity implements EssentiaTransport {

    /** 连接的源质供应器坐标（null = 未连接）。 */
    private BlockPos linkedProviderPos;

    /** 接收器每次输入/输出源质上限。 */
    public static final int TRANSFER_LIMIT = 16;

    /**
     * 待注入 AE 网络的源质缓冲（aspectId 序列化字符串 → 数量）。
     * 网络装不下的源质一律留在这里（随 NBT 持久化），等网络有空间再注入。
     */
    private final Map<String, Integer> buffer = new LinkedHashMap<>();

    private static final String NBT_BUFFER = "ThEBuffer";

    private int linkCheckCounter = 0;
    private int pullCounter = 0;
    private static final int PULL_INTERVAL = 10;
    /** 模拟管道的最大扫描距离（格）：接收器可隔空拉取该距离内的源质源。 */
    private static final int PIPE_SCAN_RANGE = 16;

    /** 服务端 tick：周期校验连接 + 主动沿模拟管道拉取源质（蒸馏器/罐/管道不主动推，需接收器拉）。 */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TileEssentiaProviderConnection be) {
        if (level.isClientSide()) return;
        if (be.linkedProviderPos != null && ++be.linkCheckCounter >= 40) {
            be.linkCheckCounter = 0;
            be.getLinkedProvider(); // 无效连接会被清除
        }
        if (++be.pullCounter >= PULL_INTERVAL) {
            be.pullCounter = 0;
            be.flushBuffer();               // 先把上次暂存的源质注入网络
            be.takeEssentiaFromNeighbors(); // 缓冲清空（网络有余量）时才继续拉取
        }
    }

    /**
     * 把缓冲里的源质注入连接的网络。网络满时保留剩余部分，下次再试。
     * @return 本次是否有源质被网络接收
     */
    private boolean flushBuffer() {
        if (buffer.isEmpty()) return false;
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return false;
        boolean moved = false;
        var iterator = buffer.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            AspectId aspect;
            try {
                aspect = AspectId.parse(entry.getKey());
            } catch (IllegalArgumentException e) {
                iterator.remove();
                continue;
            }
            int amt = entry.getValue();
            if (amt <= 0) {
                iterator.remove();
                continue;
            }
            long rejected = provider.thE_inject(aspect, amt, false);
            int accepted = amt - (int) rejected;
            if (accepted > 0) {
                int left = amt - accepted;
                if (left > 0) {
                    entry.setValue(left);
                } else {
                    iterator.remove();
                }
                moved = true;
            }
            if (rejected > 0) break; // 网络已满：剩余留在缓冲里
        }
        if (moved) setChanged();
        return moved;
    }

    /** 模拟管道：沿 6 个方向扫描，最近的有效源质源（蒸馏器/罐/管道）隔空拉取，经连接供应器注入 AE 网络。 */
    private void takeEssentiaFromNeighbors() {
        if (level == null || level.isClientSide()) return;
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return;
        // 缓冲还没清空 = 网络装不下，先别再吸（否则"吸走却存不下"）
        if (!buffer.isEmpty()) return;
        for (Direction side : Direction.values()) {
            // 沿方向行走：非传输方块 = 管道断；空管道 = 继续；有源质 = 拉取
            for (int d = 1; d <= PIPE_SCAN_RANGE; d++) {
                BlockPos sourcePos = worldPosition.relative(side, d);
                if (!level.isLoaded(sourcePos)) break;
                BlockEntity sourceTile = level.getBlockEntity(sourcePos);
                if (sourceTile == this) break;
                if (sourceTile instanceof TileEssentiaProvider) break; // 到供应器本体，防循环
                if (!(sourceTile instanceof EssentiaTransport source)) break; // 非传输方块 = 管道断
                Direction sourceSide = side.getOpposite();
                if (!source.canOutputTo(sourceSide)) break; // 不能向接收器方向输出 = 断
                int srcAmount = source.essentiaAmount(sourceSide);
                if (srcAmount <= 0) continue; // 空管道/空源 → 继续走
                int dSuck = suctionAmount(side);
                if (dSuck < source.minimumSuction() || dSuck < source.suctionAmount(sourceSide)) {
                    break;
                }
                AspectId sourceAspect = source.essentiaType(sourceSide);
                if (sourceAspect == null) break;
                // 预检：网络能收多少（rejected = 收不下的量）；一点都收不下就完全不抽取
                int want = Math.min(Math.max(srcAmount, 1), TRANSFER_LIMIT);
                long rejected = provider.thE_inject(sourceAspect, want, true);
                int room = want - (int) rejected;
                if (room <= 0) break;
                int taken = source.takeEssentia(sourceAspect, room, sourceSide, EssentiaTransferMode.EXECUTE);
                if (taken > 0) {
                    buffer.merge(sourceAspect.serialized(), taken, Integer::sum); // 先进缓冲，注入失败也不丢
                    flushBuffer();
                }
                break; // 拉到即停
            }
        }
    }

    public TileEssentiaProviderConnection(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_PROVIDER_CONNECTION.get(), pos, state);
    }

    // ===== 连接管理 =====

    public BlockPos getLinkedProviderPos() {
        return linkedProviderPos;
    }

    public boolean isLinked() {
        return linkedProviderPos != null && getLinkedProvider() != null;
    }

    /** 设置连接（无线连接器右键供应器时调用）。返回 null = 成功；否则返回失败原因文案。 */
    public String setLinkedProvider(BlockPos providerPos) {
        // 在供应器侧注册（校验数量/距离）
        if (level != null && !level.isClientSide()) {
            if (level.getBlockEntity(providerPos) instanceof TileEssentiaProvider provider) {
                String error = provider.addLinkedReceiver(worldPosition);
                if (error != null) {
                    return error; // 超限/超距，不建立连接
                }
            } else {
                return "§c目标不是有效的源质供应器";
            }
        }
        this.linkedProviderPos = providerPos;
        setChanged();
        updateConnectedState();
        return null;
    }

    public void clearLink() {
        // 从供应器侧注销
        if (level != null && !level.isClientSide() && linkedProviderPos != null
                && level.getBlockEntity(linkedProviderPos) instanceof TileEssentiaProvider provider) {
            provider.removeLinkedReceiver(worldPosition);
        }
        this.linkedProviderPos = null;
        setChanged();
        updateConnectedState();
    }

    private void updateConnectedState() {
        if (level == null || level.isClientSide()) return;
        boolean connected = linkedProviderPos != null;
        BlockState st = getBlockState();
        if (st.getValue(ModBlocks.CONNECTED) != connected) {
            level.setBlock(worldPosition, st.setValue(ModBlocks.CONNECTED, connected), 3);
            BlockEntity be = level.getBlockEntity(worldPosition);
            if (be != null && be != this) {
                be.setChanged();
            }
        }
    }

    /** 查找连接的供应器 tile。 */
    public TileEssentiaProvider getLinkedProvider() {
        if (level == null || level.isClientSide() || linkedProviderPos == null) return null;
        if (level.getBlockEntity(linkedProviderPos) instanceof TileEssentiaProvider provider) {
            // 自愈：供应器列表缺本接收器时补注册（修复旧存档/列表丢失导致的状态不一致）
            if (!provider.getLinkedReceivers().contains(worldPosition)) {
                provider.addLinkedReceiver(worldPosition);
            }
            return provider;
        }
        // 供应器被移除 → 清除连接
        linkedProviderPos = null;
        setChanged();
        updateConnectedState();
        return null;
    }

    // ===== Ren 版 EssentiaTransport（转发到连接供应器） =====

    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return true; // 支持输入源质到 AE 网络
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return true;
    }

    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    @Override
    public int suctionAmount(Direction face) {
        // 与源质供应器一致：有吸力才能被蒸馏器等设施注入源质（输入功能）
        return 8;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive() || aspect == null || amount <= 0) return 0;
        if (mode == EssentiaTransferMode.SIMULATE) {
            return (int) Math.min(provider.thE_queryAmount(aspect), Math.min(amount, TRANSFER_LIMIT));
        }
        return (int) provider.thE_extract(aspect, Math.min(amount, TRANSFER_LIMIT), false);
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        // 无线输入：先问网络能收多少，只接收装得下的部分（其余留在管道/容器里，绝不吞掉）。
        // 接收后先进缓冲，注入失败下次重试——保证源质不丢。
        if (aspect == null || amount <= 0) return 0;
        if (mode == EssentiaTransferMode.SIMULATE) {
            return Math.min(amount, TRANSFER_LIMIT); // 乐观模拟；实际执行按 room 结算
        }
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return 0;
        int want = Math.min(amount, TRANSFER_LIMIT);
        long rejected = provider.thE_inject(aspect, want, true);
        int room = want - (int) rejected;
        if (room <= 0) return 0; // 网络无源质存储或已满 → 拒收
        buffer.merge(aspect.serialized(), room, Integer::sum);
        flushBuffer();
        setChanged();
        return room;
    }

    @Override
    public AspectId essentiaType(Direction face) {
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return null;
        AspectId wanted = getNeighborWantedAspect(face);
        if (wanted != null && provider.thE_queryAmount(wanted) > 0) {
            return wanted;
        }
        return provider.thE_firstAvailable();
    }

    @Override
    public int essentiaAmount(Direction face) {
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return 0;
        AspectId wanted = getNeighborWantedAspect(face);
        if (wanted != null) {
            return (int) Math.min(provider.thE_queryAmount(wanted), Integer.MAX_VALUE);
        }
        return 0;
    }

    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    private AspectId getNeighborWantedAspect(Direction side) {
        BlockPos neighborPos = worldPosition.relative(side);
        if (level.getBlockEntity(neighborPos) instanceof EssentiaTransport transport) {
            return transport.suctionType(side.getOpposite());
        }
        return null;
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkedProviderPos != null) {
            tag.putLong("LinkedProvider", linkedProviderPos.asLong());
        }
        if (!buffer.isEmpty()) {
            CompoundTag bufferTag = new CompoundTag();
            buffer.forEach(bufferTag::putInt);
            tag.put(NBT_BUFFER, bufferTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LinkedProvider")) {
            linkedProviderPos = BlockPos.of(tag.getLong("LinkedProvider"));
        } else {
            linkedProviderPos = null;
        }
        buffer.clear();
        if (tag.contains(NBT_BUFFER)) {
            CompoundTag bufferTag = tag.getCompound(NBT_BUFFER);
            for (String key : bufferTag.getAllKeys()) {
                int amount = bufferTag.getInt(key);
                if (amount > 0) {
                    buffer.put(key, amount);
                }
            }
        }
    }
}
