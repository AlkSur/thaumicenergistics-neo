package thaumicenergistics.common.tiles;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.common.blocks.EssentiaVibrationChamberBlock;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

public class TileEssentiaVibrationChamber extends ThETileBase implements IInWorldGridNodeHost, IGridTickable, IEssentiaTransport, EssentiaTransport {

    private static final int MAX_ESSENTIA = 64;
    private static final int IDLE_POWER = 1;

    // 能量槽配置：16KFE = 16000 AE，对外最大输出 1000 AE/t

    // 懒 tick 周期：燃烧中 10 tick、空闲 40 tick（能量生产无需实时响应，低频即可）
    private static final int TICK_RATE_BURNING = 10;
    private static final int TICK_RATE_IDLE = 40;

    protected final IManagedGridNode mainNode;
    private final IGridNodeListener<TileEssentiaVibrationChamber> nodeListener = new IGridNodeListener<>() {
        @Override public void onSaveChanges(TileEssentiaVibrationChamber owner, IGridNode node) { owner.setChanged(); }
        @Override public void onStateChanged(TileEssentiaVibrationChamber owner, IGridNode node, State state) {
            if (state == State.POWER) owner.isActive = owner.mainNode.isActive();
            owner.markForUpdate();
        }
    };

    private Direction attachmentSide = Direction.NORTH;
    private int burnTicksRemaining;
    private int totalBurnTicks;
    private double aePerTick;
    /**
     * Fuel per aspect, at most {@link #MAX_ESSENTIA} in total. One shared counter let the last aspect in
     * decide the tier for the whole store: 63 cheap essentia plus one potentia burned as 64 potentia (TET-07).
     */
    private final java.util.LinkedHashMap<Aspect, Integer> fuel = new java.util.LinkedHashMap<>();
    /** Aspect of the essentia now burning (display). */
    private Aspect currentAspect;
    /** Only these aspects are pulled from the ME network; pipes may still feed any aspect (TET-08). */
    private static final java.util.List<Aspect> NETWORK_FUEL = java.util.List.of(Aspect.FIRE, Aspect.ENERGY);

    private double storedEnergy;

    public TileEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(ModItems.ESSENTIA_VIBRATION_CHAMBER_ITEM.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .addService(IGridTickable.class, this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, getBlockPos());
            updateWorkingState();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mainNode != null) mainNode.destroy();
    }

    @Override @Nullable
    public IGridNode getGridNode(Direction dir) { return mainNode.getNode(); }
    public AECableType getCableConnectionType(Direction dir) { return AECableType.SMART; }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_BURNING, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) return TickRateModulation.IDLE;
        try {
            return tickGeneration(node, ticksSinceLast);
        } finally {
            updateWorkingState();
        }
    }

    private TickRateModulation tickGeneration(IGridNode node, int ticksSinceLast) {
        IGrid grid = node.getGrid();
        if (grid == null) return TickRateModulation.IDLE;

        IEnergyService energy = grid.getService(IEnergyService.class);

        // 1) 能量槽对外输出：最多 thaumicenergistics.config.ThEConfig.chamberMaxOutput() AE/t
        if (storedEnergy > 0 && energy != null) {
            double toOutput = Math.min(storedEnergy, thaumicenergistics.config.ThEConfig.chamberMaxOutput() * ticksSinceLast);
            if (toOutput > 0) {
                double rejected = energy.injectPower(toOutput, Actionable.MODULATE);
                storedEnergy -= (toOutput - rejected);
                if (storedEnergy < 0) storedEnergy = 0;
                setChanged();
            }
        }

        // 2) 能量槽已满时停止发电（但仍抽取源质以备槽空后立即使用）
        if (storedEssentia() < MAX_ESSENTIA) {
            takeEssentiaFromNeighbors();
            if (storedEssentia() < MAX_ESSENTIA) {
                pullEssentiaFromNetwork();
            }
        }

        // 若能量槽已满（或等于），不消耗源质发电
        if (storedEnergy >= thaumicenergistics.config.ThEConfig.chamberBuffer() - 0.01) {
            // 保持燃烧状态标记，以便 Jade/UI 显示"蓄能中"
            if (isActive) return TickRateModulation.SAME;
            return TickRateModulation.IDLE;
        }

        // 3) 消耗源质燃烧 -> 能量先进槽
        if (burnTicksRemaining > 0) {
            // The last interval may be shorter than ticksSinceLast: do not pay for ticks past the burn.
            double toGenerate = aePerTick * Math.min(ticksSinceLast, burnTicksRemaining);
            double space = thaumicenergistics.config.ThEConfig.chamberBuffer() - storedEnergy;
            double accepted = Math.min(toGenerate, space);
            if (accepted > 0) {
                storedEnergy += accepted;
                if (storedEnergy > thaumicenergistics.config.ThEConfig.chamberBuffer()) storedEnergy = thaumicenergistics.config.ThEConfig.chamberBuffer();
                setChanged();
            }
            burnTicksRemaining -= ticksSinceLast;
            if (burnTicksRemaining <= 0) {
                isActive = false;
                setChanged();
            } else {
                return TickRateModulation.SAME;
            }
        }

        if (burnTicksRemaining <= 0 && storedEssentia() > 0) {
            if (startBurning()) {
                isActive = true;
                setChanged();
                return TickRateModulation.URGENT;
            }
        }
        return TickRateModulation.SAME;
    }

    private void takeEssentiaFromNeighbors() {
        int space = MAX_ESSENTIA - storedEssentia();
        if (space <= 0) return;
        for (Direction side : Direction.values()) {
            BlockPos sourcePos = worldPosition.relative(side);
            BlockEntity sourceTile = level.getBlockEntity(sourcePos);
            if (!(sourceTile instanceof IEssentiaTransport source)) continue;
            Direction sourceSide = side.getOpposite();
            boolean canOut = source.canOutputTo(sourceSide);
            Aspect srcType = source.getEssentiaType(sourceSide);
            int srcAmt = source.getEssentiaAmount(sourceSide);
            if (!canOut) continue;
            if (srcType == null) continue;
            // 所有要素均可作为燃料（火/能量效率更高，其他要素 1/2）

            if (space <= 0) return;

            int want = Math.min(1, space);
            int taken = source.takeEssentia(srcType, want, sourceSide);
            if (taken > 0) {
                addFuel(srcType, taken);
                // suctionAspect 固定为 null（通配吸力），不锁定单一类型
                setChanged();
                return;
            }
        }
    }

    private IEssentiaGrid essentiaGrid;

    private boolean getEssentiaMonitor() {
        IGridNode node = mainNode.getNode();
        if (node == null) return false;
        IGrid grid = node.getGrid();
        if (grid == null) return false;
        essentiaGrid = grid.getService(IEssentiaGrid.class);
        return essentiaGrid != null;
    }

    private void pullEssentiaFromNetwork() {
        if (!getEssentiaMonitor()) return;
        if (MAX_ESSENTIA - storedEssentia() <= 0) return;
        for (Aspect aspect : NETWORK_FUEL) {
            ResourceLocation id = TCReflection.getAspectId(aspect);
            if (id == null || essentiaGrid.getEssentiaAmount(id) <= 0) continue;
            long extracted = essentiaGrid.extractEssentia(id, 1, false);
            if (extracted > 0) {
                addFuel(aspect, (int) extracted);
                return;
            }
        }
    }

    private int storedEssentia() {
        int total = 0;
        for (int amount : fuel.values()) total += amount;
        return total;
    }

    private void addFuel(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return;
        fuel.merge(aspect, amount, Integer::sum);
        setChanged();
    }

    /** Take one unit of fuel, cheapest aspect last; null when empty. */
    @Nullable
    private Aspect takeFuel() {
        Aspect best = null;
        for (var entry : fuel.entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (best == null || tierRank(entry.getKey()) > tierRank(best)) best = entry.getKey();
        }
        if (best == null) return null;
        int left = fuel.get(best) - 1;
        if (left <= 0) fuel.remove(best); else fuel.put(best, left);
        return best;
    }

    private static int tierRank(Aspect aspect) {
        return aspect == Aspect.ENERGY ? 2 : aspect == Aspect.FIRE ? 1 : 0;
    }

    private boolean startBurning() {
        Aspect burning = takeFuel();
        if (burning == null) return false;
        currentAspect = burning;

        String tag = currentAspect.getTag();
        boolean isPotentia = currentAspect == Aspect.ENERGY || tag.equals("potentia") || tag.equals("energy");
        boolean isOther = !tag.equals("ignis") && !isPotentia;

        int burnTime;
        double power;
        if (isPotentia) {
            // 能量：1.6 倍功率，燃烧更久
            burnTime = (int) (thaumicenergistics.config.ThEConfig.chamberBurnTicks() / thaumicenergistics.config.ThEConfig.chamberPotentiaMultiplier());
            power = thaumicenergistics.config.ThEConfig.chamberIgnisAePerTick() * thaumicenergistics.config.ThEConfig.chamberPotentiaMultiplier();
        } else if (isOther) {
            // 其他所有要素：基础效率的 1/2
            burnTime = thaumicenergistics.config.ThEConfig.chamberBurnTicks() / 2;
            power = thaumicenergistics.config.ThEConfig.chamberIgnisAePerTick() * thaumicenergistics.config.ThEConfig.chamberOtherMultiplier();
        } else {
            // 火：基础效率
            burnTime = thaumicenergistics.config.ThEConfig.chamberBurnTicks() / 2;
            power = thaumicenergistics.config.ThEConfig.chamberIgnisAePerTick();
        }

        burnTicksRemaining = burnTime;
        totalBurnTicks = burnTime;
        aePerTick = power;
        return true;
    }

    @Override public boolean isConnectable(Direction side) { return true; }
    @Override public boolean canInputFrom(Direction side) { return true; }
    @Override public boolean canOutputTo(Direction side) { return true; }
    @Override public void setSuction(Aspect aspect, int amount) {
        // 通配吸力：始终吸所有源质类型，忽略外部设置
    }
    @Override public Aspect getSuctionType(Direction side) {
        return null; // null = 通配吸力：管道可输送任意类型源质
    }
    @Override public int getSuctionAmount(Direction side) { return 128; }

    private boolean isFuelAspect(Aspect aspect) {
        // 所有要素均可作为燃料（火/能量效率更高，其他要素 1/2）
        return aspect != null;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) { return 0; }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        if (!isFuelAspect(aspect) || amount <= 0) return 0;
        int toAdd = Math.min(amount, MAX_ESSENTIA - storedEssentia());
        if (toAdd <= 0) return 0;
        addFuel(aspect, toAdd);
        return toAdd;
    }

    @Override
    public Aspect getEssentiaType(Direction side) {
        for (var entry : fuel.entrySet()) {
            if (entry.getValue() > 0) return entry.getKey();
        }
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        Aspect aspect = getEssentiaType(side);
        return aspect == null ? 0 : fuel.getOrDefault(aspect, 0);
    }

    @Override public int getMinimumSuction() { return 1; }
    @Override public boolean renderExtendedTube() { return false; }

    public void setSuctionType(Aspect aspect) {
        // 通配吸力模式下无需设置类型；保留方法避免编译错误
    }

    public int getStoredEssentia() { return storedEssentia(); }
    @Nullable public Aspect getCurrentAspect() { return currentAspect; }
    public int getMaxEssentia() { return MAX_ESSENTIA; }
    public double getAePerTick() { return aePerTick; }
    public int getBurnTicksRemaining() { return burnTicksRemaining; }
    public int getTotalBurnTicks() { return totalBurnTicks; }
    public float getBurnProgress() { return totalBurnTicks > 0 ? 1f - (float)burnTicksRemaining / totalBurnTicks : 0f; }

    // 能量槽访问器
    public double getStoredEnergy() { return storedEnergy; }
    public double getMaxEnergyStorage() { return thaumicenergistics.config.ThEConfig.chamberBuffer(); }
    public double getMaxOutputPerTick() { return thaumicenergistics.config.ThEConfig.chamberMaxOutput(); }
    public float getEnergyFillProgress() { return (float)(storedEnergy / thaumicenergistics.config.ThEConfig.chamberBuffer()); }

    public void markForUpdate() {
        if (level != null) {
            updateWorkingState();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void updateWorkingState() {
        if (level == null || level.isClientSide() || isRemoved() || !level.hasChunkAt(worldPosition)) return;
        var state = getBlockState();
        boolean working = mainNode.getGrid() != null && burnTicksRemaining > 0
                && storedEnergy < thaumicenergistics.config.ThEConfig.chamberBuffer() - 0.01;
        if (state.hasProperty(EssentiaVibrationChamberBlock.WORKING)
                && state.getValue(EssentiaVibrationChamberBlock.WORKING) != working) {
            level.setBlock(worldPosition, state.setValue(EssentiaVibrationChamberBlock.WORKING, working),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("AttachSide")) attachmentSide = Direction.from3DDataValue(tag.getInt("AttachSide"));
        mainNode.loadFromNBT(tag);
        mainNode.setIdlePowerUsage(IDLE_POWER);
        burnTicksRemaining = Math.max(0, Math.min(thaumicenergistics.config.ThEConfig.chamberBurnTicks(), tag.getInt("BurnTicks")));
        totalBurnTicks = Math.max(0, Math.min(thaumicenergistics.config.ThEConfig.chamberBurnTicks(), tag.getInt("TotalBurnTicks")));
        aePerTick = Math.max(0, Math.min(thaumicenergistics.config.ThEConfig.chamberIgnisAePerTick() * Math.max(1.0, Math.max(thaumicenergistics.config.ThEConfig.chamberPotentiaMultiplier(), thaumicenergistics.config.ThEConfig.chamberOtherMultiplier())), tag.getDouble("AEPerTick")));
        storedEnergy = Math.max(0, Math.min(thaumicenergistics.config.ThEConfig.chamberBuffer(), tag.getDouble("StoredEnergy")));
        currentAspect = tag.contains("AspectTag") ? Aspect.getAspect(tag.getString("AspectTag")) : null;
        fuel.clear();
        if (tag.contains("Fuel")) {
            CompoundTag stored = tag.getCompound("Fuel");
            for (String key : stored.getAllKeys()) {
                addFuel(Aspect.getAspect(key), Math.max(0, stored.getInt(key)));
            }
        } else if (currentAspect != null) {
            // Saves before per-aspect fuel kept one counter tagged with the last aspect.
            addFuel(currentAspect, Math.max(0, tag.getInt("StoredEssentia")));
        }
        // Clamp to capacity, dropping the cheapest fuel first.
        int excess = storedEssentia() - MAX_ESSENTIA;
        for (var it = fuel.entrySet().iterator(); excess > 0 && it.hasNext(); ) {
            var entry = it.next();
            int cut = Math.min(excess, entry.getValue());
            excess -= cut;
            if (entry.getValue() - cut <= 0) it.remove(); else entry.setValue(entry.getValue() - cut);
        }
        // suctionAspect 固定为 null（通配吸力），不读取 SuctionTag
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("AttachSide", attachmentSide.get3DDataValue());
        mainNode.saveToNBT(tag);
        tag.putInt("BurnTicks", burnTicksRemaining);
        tag.putInt("TotalBurnTicks", totalBurnTicks);
        tag.putDouble("AEPerTick", aePerTick);
        CompoundTag stored = new CompoundTag();
        fuel.forEach((aspect, amount) -> stored.putInt(aspect.getTag(), amount));
        tag.put("Fuel", stored);
        tag.putDouble("StoredEnergy", storedEnergy);
        if (currentAspect != null) tag.putString("AspectTag", currentAspect.getTag());
        // suctionAspect 固定为 null（通配吸力），不写入 SuctionTag
    }
}
