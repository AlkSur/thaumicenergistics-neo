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
    private static final double BASE_AE_PER_TICK = 200.0;
    private static final int IDLE_POWER = 1;

    // 能量槽配置：16KFE = 16000 AE，对外最大输出 1000 AE/t
    public static final double MAX_ENERGY_STORAGE = 16000.0;
    public static final double MAX_OUTPUT_PER_TICK = 2000.0;

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
    private int storedEssentia;
    private Aspect currentAspect;

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
        return new TickingRequest(TICK_RATE_BURNING, TICK_RATE_IDLE, false);
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
            // 所有要素均可作为燃料（火/能量效率更高，其他要素 1/2）

            if (space <= 0) return;

            int want = Math.min(1, space);
            int taken = source.takeEssentia(srcType, want, sourceSide);
            if (taken > 0) {
                storedEssentia += taken;
                currentAspect = srcType;
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

        String tag = currentAspect.getTag();
        boolean isPotentia = currentAspect == Aspect.ENERGY || tag.equals("potentia") || tag.equals("energy");
        boolean isOther = !tag.equals("ignis") && !isPotentia;

        int burnTime;
        double power;
        if (isPotentia) {
            // 能量：1.6 倍功率，燃烧更久
            burnTime = (int)(BASE_BURN_TICKS / 1.6f);
            power = BASE_AE_PER_TICK * 1.6;
        } else if (isOther) {
            // 其他所有要素：基础效率的 1/2
            burnTime = BASE_BURN_TICKS / 2;
            power = BASE_AE_PER_TICK / 2.0;
        } else {
            // 火：基础效率
            burnTime = BASE_BURN_TICKS / 2;
            power = BASE_AE_PER_TICK;
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
        // 通配吸力模式下无需设置类型；保留方法避免编译错误
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
        tag.putInt("StoredEssentia", storedEssentia);
        tag.putDouble("StoredEnergy", storedEnergy);
        if (currentAspect != null) tag.putString("AspectTag", currentAspect.getTag());
        // suctionAspect 固定为 null（通配吸力），不写入 SuctionTag
    }
}