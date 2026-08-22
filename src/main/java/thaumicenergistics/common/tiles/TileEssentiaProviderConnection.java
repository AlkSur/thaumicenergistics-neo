package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModBlocks;

/**
 * 源质无线接收器：通过无线连接器与源质供应器建立连接后，
 * 作为纯中继（无 AE 节点），把 TC 管道/炼金塔的取源质请求转发到连接的供应器。
 */
public class TileEssentiaProviderConnection extends BlockEntity implements IEssentiaTransport, EssentiaTransport {

    /** 连接的源质供应器坐标（null = 未连接）。 */
    private BlockPos linkedProviderPos;

    /** 接收器每次输入/输出源质上限。 */
    public static final int TRANSFER_LIMIT = 16;

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
            be.takeEssentiaFromNeighbors();
        }
    }

    /** 模拟管道：沿 6 个方向扫描，最近的有效源质源（蒸馏器/罐/管道）隔空拉取，经连接供应器注入 AE 网络。 */
    private void takeEssentiaFromNeighbors() {
        if (level == null || level.isClientSide()) return;
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return;
        for (Direction side : Direction.values()) {
            if (!canInputFrom(side)) continue;
            // 沿方向行走：非传输方块 = 管道断；空管道 = 继续；有源质 = 拉取
            for (int d = 1; d <= PIPE_SCAN_RANGE; d++) {
                BlockPos sourcePos = worldPosition.relative(side, d);
                if (!level.isLoaded(sourcePos)) break;
                BlockEntity sourceTile = level.getBlockEntity(sourcePos);
                if (sourceTile == this) break;
                if (sourceTile instanceof TileEssentiaProvider) break; // 到供应器本体，防循环
                if (!(sourceTile instanceof IEssentiaTransport source)) break; // 非传输方块 = 管道断
                Direction sourceSide = side.getOpposite();
                if (!source.canOutputTo(sourceSide)) break; // 不能向接收器方向输出 = 断
                int srcAmount = source.getEssentiaAmount(sourceSide);
                if (srcAmount <= 0) continue; // 空管道/空源 → 继续走
                int dSuck = getSuctionAmount(side);
                if (dSuck < source.getMinimumSuction() || dSuck < source.getSuctionAmount(sourceSide)) {
                    break;
                }
                Aspect sourceAspect = source.getEssentiaType(sourceSide);
                if (sourceAspect == null) break;
                // 直接拉取并注入（injectEssentia 返回 0 = 全部接受，无需模拟预检）
                int want = Math.min(Math.max(srcAmount, 1), TRANSFER_LIMIT);
                int taken = source.takeEssentia(sourceAspect, want, sourceSide);
                if (taken > 0) {
                    provider.thE_inject(sourceAspect, taken, false);
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

    // ===== IEssentiaTransport（转发到连接供应器） =====

    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true; // 支持输入源质到 AE 网络
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
        return null;
    }

    @Override
    public int getSuctionAmount(Direction side) {
        // 与源质供应器一致：有吸力才能被蒸馏器等设施注入源质（输入功能）
        return 8;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) {
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive() || aspect == null || amount <= 0) return 0;
        return (int) provider.thE_extract(aspect, Math.min(amount, TRANSFER_LIMIT), false);
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        // 无线输入：转发到连接供应器注入 AE 网络，每次上限 16
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive() || aspect == null || amount <= 0) return 0;
        return (int) provider.thE_inject(aspect, Math.min(amount, TRANSFER_LIMIT), false);
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return 0;
        Aspect wanted = getNeighborWantedAspect(side);
        if (wanted != null) {
            return (int) Math.min(provider.thE_queryAmount(wanted), Integer.MAX_VALUE);
        }
        return 0;
    }

    @Override
    public Aspect getEssentiaType(Direction side) {
        TileEssentiaProvider provider = getLinkedProvider();
        if (provider == null || !provider.thE_isActive()) return null;
        Aspect wanted = getNeighborWantedAspect(side);
        if (wanted != null && provider.thE_queryAmount(wanted) > 0) {
            return wanted;
        }
        return provider.thE_firstAvailable();
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    private Aspect getNeighborWantedAspect(Direction side) {
        BlockPos neighborPos = worldPosition.relative(side);
        if (level.getBlockEntity(neighborPos) instanceof IEssentiaTransport transport) {
            return transport.getSuctionType(side.getOpposite());
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LinkedProvider")) {
            linkedProviderPos = BlockPos.of(tag.getLong("LinkedProvider"));
        } else {
            linkedProviderPos = null;
        }
    }
}
