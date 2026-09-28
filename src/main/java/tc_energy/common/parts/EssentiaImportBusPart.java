package tc_energy.common.parts;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManagerBuilder;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.IOBusPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import tc_energy.ThaumicEnergistics;
import tc_energy.api.storage.AspectStack;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.appeng.AEssentiaKeyType;
import tc_energy.common.integration.tc.EssentiaConversionHelper;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.init.ModMenuTypes;

import java.util.Set;

public class EssentiaImportBusPart extends IOBusPart
        implements KeyTypeSelectionHost {

    @PartModels
    public static final ResourceLocation RL_BASE =
            ThaumicEnergistics.id("parts/essentia_import_bus_base");
    @PartModels
    public static final ResourceLocation RL_OFF =
            ThaumicEnergistics.id("parts/essentia_import_bus_off");
    @PartModels
    public static final ResourceLocation RL_HAS_CHANNEL =
            ThaumicEnergistics.id("parts/essentia_import_bus_has_channel");
    @PartModels
    public static final ResourceLocation RL_ON =
            ThaumicEnergistics.id("parts/essentia_import_bus_on");

    public static final PartModel MODEL_OFF = new PartModel(RL_BASE, RL_OFF);
    public static final PartModel MODEL_HAS_CHANNEL = new PartModel(RL_BASE, RL_HAS_CHANNEL);
    public static final PartModel MODEL_ON = new PartModel(RL_BASE, RL_ON);

    private static final int TRANSFER_RATE = 8;
    private static final double IDLE_POWER = 0.5;
    private static final double TRANSFER_POWER = EssentiaConversionHelper.AE_PER_ESSENTIA;

    private static final String NBT_BUFFER_ASPECT = "ThEBufferAspect";
    private static final String NBT_BUFFER_AMOUNT = "ThEBufferAmount";

    private boolean isWorkingFlag = false;

    /**
     * 待注入 AE 网络的源质缓冲（随总线 NBT 持久化）。
     * <p>
     * 网络装不下（无源质存储 / 已满 / 模拟与实插不一致）时源质留在这里重试，
     * 绝不丢弃；缓冲非空期间也不再从容器抽取，避免"吸走却存不下"。
     */
    private AspectStack buffer = AspectStack.EMPTY;

    /**
     * 单次传输量 = 基础速率 × 每 tick 操作次数（受加速卡影响）。
     * AE2 加速卡语义：0卡=1、1卡=8、2卡=32、3卡=64、4卡=96（getOperationsPerTick）。
     */
    private int getTransferRate() {
        return TRANSFER_RATE * Math.max(1, getOperationsPerTick());
    }

    private final KeyTypeSelection keyTypeSelection = new KeyTypeSelection(
            selection -> { },
            keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public EssentiaImportBusPart(IPartItem<?> partItem) {
        super(TickRates.ImportBus, Set.of(AEssentiaKeyType.INSTANCE), partItem);
        this.getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 11, 10, 10, 13);
        bch.addBox(5, 5, 13, 11, 11, 14);
        bch.addBox(4, 4, 14, 12, 12, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        if (isWorkingFlag) return MODEL_ON;
        if (getMainNode() != null && getMainNode().isOnline()) return MODEL_HAS_CHANNEL;
        return MODEL_OFF;
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        this.isWorkingFlag = false;

        // 1) 先冲缓冲：网络有空间时把上次暂存的源质注入
        boolean worked = flushBuffer(grid);

        Direction side = this.getSide();
        if (side == null) return worked;
        BlockPos adjacentPos = this.getBlockEntity().getBlockPos().relative(side);
        Level levelRaw = this.getLevel();
        if (!(levelRaw instanceof ServerLevel level)) return worked;
        if (!level.isLoaded(adjacentPos)) return worked;
        BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);
        if (adjacentBE == null) return worked;

        // 2) 缓冲仍有剩余 = 网络装不下 → 不再从容器抽取（源质留在原容器里）
        if (!this.buffer.isEmpty()) return worked;

        // 3) 预检 1：先窥视容器能给什么（非破坏性）。无源质直接退出。
        int rate = getTransferRate();
        AspectStack available = TCReflection.peekEssentia(adjacentBE, rate);
        if (available == null || available.isEmpty()) return worked;
        AEssentiaKey key = AEssentiaKey.of(available.aspectId());
        if (key == null) return worked;

        // 4) 预检 2：网络能接收多少。为 0（无源质存储/已满）则完全不抽取。
        long acceptable = grid.getStorageService().getInventory().insert(
                key, available.amount(), Actionable.SIMULATE, IActionSource.ofMachine(this));
        if (acceptable <= 0) return worked;

        int toExtract = (int) Math.min(available.amount(), acceptable);
        AspectStack extracted = TCReflection.extractEssentia(adjacentBE, available.aspectId(), toExtract);
        if (extracted == null || extracted.isEmpty()) return worked;

        long injected = this.injectToNetwork(grid, extracted);
        if (injected < extracted.amount()) {
            // 模拟与实插不一致（例如挂载的容器存储无法模拟）→ 剩余留在缓冲里重试，绝不归还/丢弃
            this.buffer = new AspectStack(extracted.aspectId(), extracted.amount() - injected);
            markForSave();
        }

        double powerCost = injected * TRANSFER_POWER;
        grid.getEnergyService().extractAEPower(powerCost, Actionable.MODULATE, PowerMultiplier.CONFIG);

        this.isWorkingFlag = injected > 0 || worked;
        return injected > 0 || worked;
    }

    /** 把缓冲里的源质注入网络。@return 本次是否注入了任意数量 */
    private boolean flushBuffer(IGrid grid) {
        if (this.buffer.isEmpty()) return false;
        long injected = this.injectToNetwork(grid, this.buffer);
        if (injected <= 0) return false;
        long left = this.buffer.amount() - injected;
        this.buffer = left > 0 ? new AspectStack(this.buffer.aspectId(), left) : AspectStack.EMPTY;
        markForSave();
        this.isWorkingFlag = true;
        return true;
    }

    private void markForSave() {
        if (getHost() != null) {
            getHost().markForSave();
        }
    }

    private long injectToNetwork(IGrid grid, AspectStack stack) {
        AEssentiaKey key = AEssentiaKey.of(stack.aspectId());
        if (key == null) return 0;
        return grid.getStorageService().getInventory().insert(
                key, stack.amount(), Actionable.MODULATE,
                IActionSource.ofMachine(this));
    }

    @Override
    public void readFromNBT(CompoundTag extra, HolderLookup.Provider registries) {
        super.readFromNBT(extra, registries);
        this.keyTypeSelection.readFromNBT(extra, registries);
        this.buffer = AspectStack.EMPTY;
        if (extra.contains(NBT_BUFFER_ASPECT) && extra.getLong(NBT_BUFFER_AMOUNT) > 0) {
            ResourceLocation id = ResourceLocation.tryParse(extra.getString(NBT_BUFFER_ASPECT));
            if (id != null) {
                this.buffer = new AspectStack(id, extra.getLong(NBT_BUFFER_AMOUNT));
            }
        }
    }

    @Override
    public void writeToNBT(CompoundTag extra, HolderLookup.Provider registries) {
        super.writeToNBT(extra, registries);
        this.keyTypeSelection.writeToNBT(extra);
        if (!this.buffer.isEmpty()) {
            extra.putString(NBT_BUFFER_ASPECT, this.buffer.aspectId().toString());
            extra.putLong(NBT_BUFFER_AMOUNT, this.buffer.amount());
        }
    }

    @Override
    protected MenuType<?> getMenuType() {
        return ModMenuTypes.ESSENTIA_IMPORT_BUS.get();
    }

    @Override
    public int getUpgradeSlots() { return 4; }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }
}

