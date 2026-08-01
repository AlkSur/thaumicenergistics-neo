package thaumicenergistics.common.parts;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
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
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModMenuTypes;

import java.util.Set;

public class EssentiaStorageBusPart extends IOBusPart
        implements KeyTypeSelectionHost {

    @PartModels
    public static final ResourceLocation RL_BASE =
            ThaumicEnergistics.id("parts/essentia_storage_bus_base");
    @PartModels
    public static final ResourceLocation RL_OFF =
            ThaumicEnergistics.id("parts/essentia_storage_bus_off");
    @PartModels
    public static final ResourceLocation RL_HAS_CHANNEL =
            ThaumicEnergistics.id("parts/essentia_storage_bus_has_channel");
    @PartModels
    public static final ResourceLocation RL_ON =
            ThaumicEnergistics.id("parts/essentia_storage_bus_on");

    public static final PartModel MODEL_OFF = new PartModel(RL_BASE, RL_OFF);
    public static final PartModel MODEL_HAS_CHANNEL = new PartModel(RL_BASE, RL_HAS_CHANNEL);
    public static final PartModel MODEL_ON = new PartModel(RL_BASE, RL_ON);

    private static final double IDLE_POWER = 1.0;
    private static final int TRANSFER_RATE = 8;
    private boolean isWorkingFlag = false;

    private final KeyTypeSelection keyTypeSelection = new KeyTypeSelection(
            selection -> { },
            keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public EssentiaStorageBusPart(IPartItem<?> partItem) {
        super(TickRates.StorageBus, Set.of(AEssentiaKeyType.INSTANCE), partItem);
        this.getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(5, 5, 12, 11, 11, 14);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(3, 3, 15, 13, 13, 16);
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

        Direction side = this.getSide();
        if (side == null) return false;
        BlockPos adjacentPos = this.getBlockEntity().getBlockPos().relative(side);
        Level levelRaw = this.getLevel();
        if (!(levelRaw instanceof ServerLevel level)) return false;
        if (!level.isLoaded(adjacentPos)) return false;
        BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);
        if (adjacentBE == null) return false;

        AspectStack extracted = TCReflection.extractEssentia(adjacentBE, TRANSFER_RATE);
        if (extracted == null || extracted.isEmpty()) return false;

        this.insertIntoNetwork(grid, extracted);
        this.isWorkingFlag = true;
        return true;
    }

    private void insertIntoNetwork(IGrid grid, AspectStack stack) {
        AEssentiaKey key = AEssentiaKey.of(stack.aspectId());
        grid.getStorageService().getInventory().insert(
                key, stack.amount(), Actionable.MODULATE,
                IActionSource.ofMachine(this));
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public void readFromNBT(CompoundTag extra, HolderLookup.Provider registries) {
        super.readFromNBT(extra, registries);
        this.keyTypeSelection.readFromNBT(extra, registries);
    }

    @Override
    public void writeToNBT(CompoundTag extra, HolderLookup.Provider registries) {
        super.writeToNBT(extra, registries);
        this.keyTypeSelection.writeToNBT(extra);
    }

    @Override
    protected MenuType<?> getMenuType() {
        return ModMenuTypes.ESSENTIA_STORAGE_BUS.get();
    }

    @Override
    public int getUpgradeSlots() { return 4; }
}
