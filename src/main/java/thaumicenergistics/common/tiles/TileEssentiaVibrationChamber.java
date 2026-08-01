package thaumicenergistics.common.tiles;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModBlockEntities;

public class TileEssentiaVibrationChamber extends ThETileBase implements IInWorldGridNodeHost, IGridTickable, IEssentiaTransport, EssentiaTransport {

    private static final int MAX_ESSENTIA = 64;
    private static final int BASE_BURN_TICKS = 800;
    private static final double BASE_AE_PER_TICK = 20.0;
    private static final int IDLE_POWER = 1;

    // 能量槽配置：8KFE = 8000 AE，对外最大输出 400 AE/t
    public static final double MAX_ENERGY_STORAGE = 8000.0;
    public static final double MAX_OUTPUT_PER_TICK = 400.0;

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
    private int storedEssentia;
    private Aspect currentAspect;
    private Aspect suctionAspect = Aspect.ENERGY;

    private double storedEnergy;

    public TileEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(thaumicenergistics.init.ModItems.ESSENTIA_VIBRATION_CHAMBER_ITEM.get())
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
        return new TickingRequest(10, 40, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) return TickRateModulation.IDLE;
        IGrid grid = node.getGrid();
        if (grid == null) return TickRateModulation.IDLE;

        IEnergyService energy = grid.getService(IEnergyService.class);

        // 1) 能量槽对外输出：最多 MAX_OUTPUT_PER_TICK AE/t
        if (storedEnergy > 0 && energy != null) {
            double toOutput = Math.min(storedEnergy, MAX_OUTPUT_PER_TICK * ticksSinceLast);
            if (toOutput > 0) {
                double rejected = energy.injectPower(toOutput, Actionable.MODULATE);
                storedEnergy -= (toOutput - rejected);
                if (storedEnergy < 0) storedEnergy = 0;
                setChanged();
            }
        }

        // 2) 能量槽已满时停止发电（但仍抽取源质以备槽空后立即使用）
        if (storedEssentia < MAX_ESSENTIA) {
            takeEssentiaFromNeighbors();
            if (storedEssentia < MAX_ESSENTIA) {
                pullEssentiaFromNetwork();
            }
        }

        // 若能量槽已满（或等于），不消耗源质发电
        if (storedEnergy >= MAX_ENERGY_STORAGE - 0.01) {
            // 保持燃烧状态标记，以便 Jade/UI 显示"蓄能中"
            if (isActive) return TickRateModulation.SAME;
            return TickRateModulation.IDLE;
        }

        // 3) 消耗源质燃烧 -> 能量先进槽
        if (burnTicksRemaining > 0) {
            double toGenerate = aePerTick * ticksSinceLast;
            double space = MAX_ENERGY_STORAGE - storedEnergy;
            double accepted = Math.min(toGenerate, space);
            if (accepted > 0) {
                storedEnergy += accepted;
                if (storedEnergy > MAX_ENERGY_STORAGE) storedEnergy = MAX_ENERGY_STORAGE;
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

        if (burnTicksRemaining <= 0 && storedEssentia > 0) {
            if (startBurning()) {
                storedEssentia--;
                isActive = true;
                setChanged();
                return TickRateModulation.URGENT;
            }
        }
        return TickRateModulation.SAME;
    }

    private void takeEssentiaFromNeighbors() {
        int space = MAX_ESSENTIA - storedEssentia;
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
            String srcTag = srcType.getTag();
            if (!srcTag.equals("ignis") && !srcTag.equals("potentia") && !srcTag.equals("energy")) continue;

            if (space <= 0) return;

            int want = Math.min(1, space);
            int taken = source.takeEssentia(srcType, want, sourceSide);
            if (taken > 0) {
                storedEssentia += taken;
                currentAspect = srcType;
                suctionAspect = srcType;
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
        int space = MAX_ESSENTIA - storedEssentia;
        if (space <= 0) return;

        if (currentAspect != null && isFuelAspect(currentAspect)) {
            ResourceLocation id = TCReflection.getAspectId(currentAspect);
            if (id != null && essentiaGrid.getEssentiaAmount(id) > 0) {
                long extracted = essentiaGrid.extractEssentia(id, 1, false);
                if (extracted > 0) {
                    storedEssentia += (int) extracted;
                    setChanged();
                    return;
                }
            }
        }

        var list = essentiaGrid.getEssentiaList();
        for (var stack : list) {
            if (stack.amount() <= 0) continue;
            Aspect aspect = Aspect.getAspect(stack.aspectId().getPath());
            if (!isFuelAspect(aspect)) continue;
            long extracted = essentiaGrid.extractEssentia(stack.aspectId(), 1, false);
            if (extracted > 0) {
                storedEssentia += (int) extracted;
                currentAspect = aspect;
                setChanged();
                return;
            }
        }
    }

    private boolean startBurning() {
        if (currentAspect == null) currentAspect = Aspect.FIRE;

        boolean isPotentia = currentAspect != null && (currentAspect == Aspect.ENERGY || currentAspect.getTag().equals("potentia") || currentAspect.getTag().equals("energy"));
        int burnTime = isPotentia ? (int)(BASE_BURN_TICKS / 1.6f) : BASE_BURN_TICKS / 2;
        double power = isPotentia ? BASE_AE_PER_TICK * 1.6 : BASE_AE_PER_TICK;

        burnTicksRemaining = burnTime;
        totalBurnTicks = burnTime;
        aePerTick = power;
        return true;
    }

    @Override public boolean isConnectable(Direction side) { return true; }
    @Override public boolean canInputFrom(Direction side) { return true; }
    @Override public boolean canOutputTo(Direction side) { return true; }
    @Override public void setSuction(Aspect aspect, int amount) {
        this.suctionAspect = aspect;
    }
    @Override public Aspect getSuctionType(Direction side) {
        return suctionAspect;
    }
    @Override public int getSuctionAmount(Direction side) { return 128; }

    private boolean isFuelAspect(Aspect aspect) {
        if (aspect == null) return false;
        String tag = aspect.getTag();
        return tag.equals("ignis") || tag.equals("potentia") || tag.equals("energy");
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) { return 0; }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        if (storedEssentia >= MAX_ESSENTIA) return 0;
        if (!isFuelAspect(aspect)) return 0;

        int space = MAX_ESSENTIA - storedEssentia;
        int toAdd = Math.min(amount, space);
        storedEssentia += toAdd;
        currentAspect = aspect;
        if (toAdd > 0) setChanged();
        return toAdd;
    }

    @Override
    public Aspect getEssentiaType(Direction side) {
        if (storedEssentia <= 0) return null;
        return currentAspect;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return storedEssentia;
    }

    @Override public int getMinimumSuction() { return 1; }
    @Override public boolean renderExtendedTube() { return false; }

    public void setSuctionType(Aspect aspect) {
        if (aspect == Aspect.FIRE || aspect == Aspect.ENERGY) {
            suctionAspect = aspect;
            setChanged();
            markForUpdate();
        }
    }

    public int getStoredEssentia() { return storedEssentia; }
    public int getMaxEssentia() { return MAX_ESSENTIA; }
    public double getAePerTick() { return aePerTick; }
    public int getBurnTicksRemaining() { return burnTicksRemaining; }
    public int getTotalBurnTicks() { return totalBurnTicks; }
    public float getBurnProgress() { return totalBurnTicks > 0 ? 1f - (float)burnTicksRemaining / totalBurnTicks : 0f; }

    // 能量槽访问器
    public double getStoredEnergy() { return storedEnergy; }
    public double getMaxEnergyStorage() { return MAX_ENERGY_STORAGE; }
    public double getMaxOutputPerTick() { return MAX_OUTPUT_PER_TICK; }
    public float getEnergyFillProgress() { return (float)(storedEnergy / MAX_ENERGY_STORAGE); }

    public void markForUpdate() {
        if (level != null) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("AttachSide")) attachmentSide = Direction.from3DDataValue(tag.getInt("AttachSide"));
        mainNode.loadFromNBT(tag);
        mainNode.setIdlePowerUsage(IDLE_POWER);
        burnTicksRemaining = tag.getInt("BurnTicks");
        totalBurnTicks = tag.getInt("TotalBurnTicks");
        aePerTick = tag.getDouble("AEPerTick");
        storedEssentia = tag.getInt("StoredEssentia");
        storedEnergy = tag.getDouble("StoredEnergy");
        if (storedEnergy > MAX_ENERGY_STORAGE) storedEnergy = MAX_ENERGY_STORAGE;
        if (tag.contains("AspectTag")) currentAspect = Aspect.getAspect(tag.getString("AspectTag"));
        if (tag.contains("SuctionTag")) suctionAspect = Aspect.getAspect(tag.getString("SuctionTag"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("AttachSide", attachmentSide.get3DDataValue());
        mainNode.saveToNBT(tag);
        tag.putInt("BurnTicks", burnTicksRemaining);
        tag.putInt("TotalBurnTicks", totalBurnTicks);
        tag.putDouble("AEPerTick", aePerTick);
        tag.putInt("StoredEssentia", storedEssentia);
        tag.putDouble("StoredEnergy", storedEnergy);
        if (currentAspect != null) tag.putString("AspectTag", currentAspect.getTag());
        if (suctionAspect != null) tag.putString("SuctionTag", suctionAspect.getTag());
    }
}