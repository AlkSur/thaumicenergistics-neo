package tc_energy.common.tiles;

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

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import tc_energy.ThaumicEnergistics;
import tc_energy.api.grid.IEssentiaGrid;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.init.ModBlockEntities;

public class TileEssentiaVibrationChamber extends ThETileBase implements IInWorldGridNodeHost, IGridTickable, EssentiaTransport {

    private static final int MAX_ESSENTIA = 64;
    private static final int BASE_BURN_TICKS = 800;
    private static final double BASE_AE_PER_TICK = 200.0;
    private static final int IDLE_POWER = 1;

    // 鑳介噺妲介厤缃細16KFE = 16000 AE锛屽澶栨渶澶ц緭鍑?1000 AE/t
    public static final double MAX_ENERGY_STORAGE = 16000.0;
    public static final double MAX_OUTPUT_PER_TICK = 2000.0;

    // 鎳?tick 鍛ㄦ湡锛氱噧鐑т腑 10 tick銆佺┖闂?40 tick锛堣兘閲忕敓浜ф棤闇€瀹炴椂鍝嶅簲锛屼綆棰戝嵆鍙級
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
    private AspectId currentAspect;

    private double storedEnergy;

    public TileEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(tc_energy.init.ModItems.ESSENTIA_VIBRATION_CHAMBER_ITEM.get())
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

        // 1) 鑳介噺妲藉澶栬緭鍑猴細鏈€澶?MAX_OUTPUT_PER_TICK AE/t
        if (storedEnergy > 0 && energy != null) {
            double toOutput = Math.min(storedEnergy, MAX_OUTPUT_PER_TICK * ticksSinceLast);
            if (toOutput > 0) {
                double rejected = energy.injectPower(toOutput, Actionable.MODULATE);
                storedEnergy -= (toOutput - rejected);
                if (storedEnergy < 0) storedEnergy = 0;
                setChanged();
            }
        }

        // 2) 鑳介噺妲藉凡婊℃椂鍋滄鍙戠數锛堜絾浠嶆娊鍙栨簮璐ㄤ互澶囨Ы绌哄悗绔嬪嵆浣跨敤锛?
        if (storedEssentia < MAX_ESSENTIA) {
            takeEssentiaFromNeighbors();
            if (storedEssentia < MAX_ESSENTIA) {
                pullEssentiaFromNetwork();
            }
        }

        // 鑻ヨ兘閲忔Ы宸叉弧锛堟垨绛変簬锛夛紝涓嶆秷鑰楁簮璐ㄥ彂鐢?
        if (storedEnergy >= MAX_ENERGY_STORAGE - 0.01) {
            // 淇濇寔鐕冪儳鐘舵€佹爣璁帮紝浠ヤ究 Jade/UI 鏄剧ず"钃勮兘涓?
            if (isActive) return TickRateModulation.SAME;
            return TickRateModulation.IDLE;
        }

        // 3) 娑堣€楁簮璐ㄧ噧鐑?-> 鑳介噺鍏堣繘妲?
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
            if (!(sourceTile instanceof EssentiaTransport source)) continue;
            Direction sourceSide = side.getOpposite();
            boolean canOut = source.canOutputTo(sourceSide);
            AspectId srcType = source.essentiaType(sourceSide);
            int srcAmt = source.essentiaAmount(sourceSide);
            if (!canOut) continue;
            if (srcType == null) continue;
            // 鎵€鏈夎绱犲潎鍙綔涓虹噧鏂欙紙鐏?鑳介噺鏁堢巼鏇撮珮锛屽叾浠栬绱?1/2锛?

            if (space <= 0) return;

            int want = Math.min(1, space);
            int taken = source.takeEssentia(srcType, want, sourceSide, EssentiaTransferMode.EXECUTE);
            if (taken > 0) {
                storedEssentia += taken;
                currentAspect = srcType;
                // suctionAspect 鍥哄畾涓?null锛堥€氶厤鍚稿姏锛夛紝涓嶉攣瀹氬崟涓€绫诲瀷
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
            ResourceLocation id = currentAspect.value();
            if (essentiaGrid.getEssentiaAmount(id) > 0) {
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
            AspectId aspect = AspectId.of(stack.aspectId());
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
        if (currentAspect == null) currentAspect = AspectId.parse("ignis");

        String tag = currentAspect.value().getPath();
        boolean isPotentia = tag.equals("potentia");
        boolean isOther = !tag.equals("ignis") && !isPotentia;

        int burnTime;
        double power;
        if (isPotentia) {
            // 鑳介噺锛?.6 鍊嶅姛鐜囷紝鐕冪儳鏇翠箙
            burnTime = (int)(BASE_BURN_TICKS / 1.6f);
            power = BASE_AE_PER_TICK * 1.6;
        } else if (isOther) {
            // 鍏朵粬鎵€鏈夎绱狅細鍩虹鏁堢巼鐨?1/2
            burnTime = BASE_BURN_TICKS / 2;
            power = BASE_AE_PER_TICK / 2.0;
        } else {
            // 鐏細鍩虹鏁堢巼
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
    @Override public AspectId suctionType(Direction side) {
        return null; // null = wildcard suction: accepts any aspect type
    }
    @Override public int suctionAmount(Direction side) { return 128; }

    private boolean isFuelAspect(AspectId aspect) {
        // 鎵€鏈夎绱犲潎鍙綔涓虹噧鏂欙紙鐏?鑳介噺鏁堢巼鏇撮珮锛屽叾浠栬绱?1/2锛?
        return aspect != null;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction side, EssentiaTransferMode mode) { return 0; }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction side, EssentiaTransferMode mode) {
        if (aspect == null || amount <= 0) return 0;
        if (mode == EssentiaTransferMode.SIMULATE) {
            return Math.min(amount, MAX_ESSENTIA - storedEssentia);
        }
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
    public AspectId essentiaType(Direction side) {
        if (storedEssentia <= 0) return null;
        return currentAspect;
    }

    @Override
    public int essentiaAmount(Direction side) {
        return storedEssentia;
    }

    @Override public int minimumSuction() { return 1; }
    @Override public boolean renderExtendedTube() { return false; }

    public void setSuctionType(AspectId aspect) {
        // 閫氶厤鍚稿姏妯″紡涓嬫棤闇€璁剧疆绫诲瀷锛涗繚鐣欐柟娉曢伩鍏嶇紪璇戦敊璇?
    }

    public int getStoredEssentia() { return storedEssentia; }
    public int getMaxEssentia() { return MAX_ESSENTIA; }
    public double getAePerTick() { return aePerTick; }
    public int getBurnTicksRemaining() { return burnTicksRemaining; }
    public int getTotalBurnTicks() { return totalBurnTicks; }
    public float getBurnProgress() { return totalBurnTicks > 0 ? 1f - (float)burnTicksRemaining / totalBurnTicks : 0f; }

    // 鑳介噺妲借闂櫒
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
        if (tag.contains("AspectTag")) currentAspect = AspectId.parse(tag.getString("AspectTag"));
        // suctionAspect 鍥哄畾涓?null锛堥€氶厤鍚稿姏锛夛紝涓嶈鍙?SuctionTag
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
        if (currentAspect != null) tag.putString("AspectTag", currentAspect.serialized());
        // suctionAspect 鍥哄畾涓?null锛堥€氶厤鍚稿姏锛夛紝涓嶅啓鍏?SuctionTag
    }
}