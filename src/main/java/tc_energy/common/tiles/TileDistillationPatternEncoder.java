package tc_energy.common.tiles;

import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import tc_energy.init.ModBlockEntities;

public class TileDistillationPatternEncoder extends ThETileBase {
    public static final int SLOT_SOURCE_ITEM = 0;
    public static final int SLOT_BLANK_PATTERNS = 1;
    public static final int SLOT_ENCODED_PATTERN = 2;
    public static final int SLOT_TOTAL_COUNT = 3;

    private static final String NBTKEY_INVENTORY = "inventory";

    public final ItemStackHandler inventory = new ItemStackHandler(SLOT_TOTAL_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // blank 槽放宽：接受任意物品（Encode 时验证是否为 AE2 空白样板）
            if (slot == SLOT_BLANK_PATTERNS) return true;
            if (slot == SLOT_ENCODED_PATTERN) return false;
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public TileDistillationPatternEncoder(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLATION_ENCODER.get(), pos, state);
    }

    public boolean hasPatterns() {
        return !inventory.getStackInSlot(SLOT_ENCODED_PATTERN).isEmpty()
                || !inventory.getStackInSlot(SLOT_BLANK_PATTERNS).isEmpty();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains(NBTKEY_INVENTORY)) {
            inventory.deserializeNBT(reg, tag.getCompound(NBTKEY_INVENTORY));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put(NBTKEY_INVENTORY, inventory.serializeNBT(reg));
    }
}
