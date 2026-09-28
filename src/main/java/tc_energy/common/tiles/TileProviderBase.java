package tc_energy.common.tiles;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import org.jetbrains.annotations.Nullable;

import tc_energy.api.grid.IEssentiaGrid;
import java.util.EnumSet;
import java.util.Set;

public abstract class TileProviderBase extends ThETileBase implements IInWorldGridNodeHost {

    private static final String NBT_ATTACH_SIDE = "AttachSide";
    private static final String NBT_COLOR = "TEColor";
    private static final String NBT_COLOR_FORCED = "ColorForced";

    protected final IManagedGridNode mainNode;
    protected Direction attachmentSide = Direction.NORTH;
    protected AEColor providerColor = AEColor.TRANSPARENT;
    protected boolean isColorForced;
    protected IEssentiaGrid essentiaGrid;

    private final IGridNodeListener<TileProviderBase> nodeListener = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(TileProviderBase owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(TileProviderBase owner, IGridNode node, State state) {
            if (state == State.POWER) {
                owner.onPowerChange(owner.isActive());
            }
            owner.markForUpdate();
        }
    };

    public TileProviderBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(getItemFromTile())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    protected abstract ItemStack getItemFromTile();
    protected abstract double getIdlePowerUsage();

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
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(NBT_ATTACH_SIDE)) {
            attachmentSide = Direction.from3DDataValue(tag.getInt(NBT_ATTACH_SIDE));
        }
        if (tag.contains(NBT_COLOR)) {
            providerColor = AEColor.values()[tag.getInt(NBT_COLOR)];
            mainNode.setGridColor(providerColor);
        }
        isColorForced = tag.getBoolean(NBT_COLOR_FORCED);
        mainNode.loadFromNBT(tag);
        mainNode.setIdlePowerUsage(getIdlePowerUsage());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(NBT_ATTACH_SIDE, attachmentSide.get3DDataValue());
        tag.putInt(NBT_COLOR, providerColor.ordinal());
        tag.putBoolean(NBT_COLOR_FORCED, isColorForced);
        mainNode.saveToNBT(tag);
    }

    public void markForUpdate() {
        if (level != null) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ===== IInWorldGridNodeHost =====

    @Override
    @Nullable
    public IGridNode getGridNode(Direction dir) {
        var node = mainNode.getNode();
        return node;
    }

    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    public Set<Direction> getGridConnectableSides() {
        return EnumSet.allOf(Direction.class);
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    public boolean isActive() {
        return mainNode.isActive();
    }

    protected void onPowerChange(boolean isPowered) {
    }

    // ===== Essentia 网格访问 =====

    protected boolean getEssentiaMonitor() {
        IGridNode node = mainNode.getNode();
        if (node == null) return false;
        IGrid grid = node.getGrid();
        if (grid == null) return false;
        essentiaGrid = grid.getService(IEssentiaGrid.class);
        return essentiaGrid != null;
    }

    protected long getAspectAmountInNetwork(AspectId aspect) {
        if (!getEssentiaMonitor() || aspect == null) return 0;
        return essentiaGrid.getEssentiaAmount(aspect.value());
    }

    protected long extractEssentiaFromNetwork(AspectId aspect, long amount, boolean simulate) {
        if (!getEssentiaMonitor() || aspect == null) return 0;
        return essentiaGrid.extractEssentia(aspect.value(), amount, simulate);
    }

    protected long injectEssentiaToNetwork(AspectId aspect, long amount, boolean simulate) {
        if (!getEssentiaMonitor() || aspect == null) return 0;
        return essentiaGrid.injectEssentia(aspect.value(), amount, simulate);
    }
}