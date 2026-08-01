package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumicenergistics.common.items.ItemEssentiaCell;
import thaumicenergistics.init.ModBlockEntities;

/**
 * 源质元件工作台方块实体：持久化保存一个源质元件（cell），并提供分区编辑入口。
 * 对应 1.7.10 的 {@code TileEssentiaCellWorkbench}，分区数据存储在 cell 物品的 AE2 CellConfig 上。
 */
public class TileEssentiaCellWorkbench extends BlockEntity {

    public static final int SLOT_CELL = 0;
    private static final String NBT_KEY_CELL_INVENTORY = "CellInventory";

    /** 单格 cell 槽，只允许放入 ItemEssentiaCell。 */
    private final ItemStackHandler cellInventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || stack.getItem() instanceof ItemEssentiaCell;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            setChanged();
        }
    };

    public TileEssentiaCellWorkbench(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_CELL_WORKBENCH.get(), pos, state);
    }

    public ItemStackHandler getCellInventory() {
        return cellInventory;
    }

    /** 当前放入的源质元件，空则为 EMPTY。 */
    public ItemStack getCell() {
        return cellInventory.getStackInSlot(SLOT_CELL);
    }

    public boolean hasCell() {
        return !cellInventory.getStackInSlot(SLOT_CELL).isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(NBT_KEY_CELL_INVENTORY, cellInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(NBT_KEY_CELL_INVENTORY)) {
            cellInventory.deserializeNBT(registries, tag.getCompound(NBT_KEY_CELL_INVENTORY));
        }
    }
}
