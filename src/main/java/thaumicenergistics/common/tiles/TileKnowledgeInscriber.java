package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

public class TileKnowledgeInscriber extends ThETileBase {
    public static final int KCORE_SLOT = 0;
    private static final String NBTKEY_KCORE = "kcore";

    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.KNOWLEDGE_CORE.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public TileKnowledgeInscriber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KNOWLEDGE_INSCRIBER.get(), pos, state);
    }

    public boolean hasKCore() {
        return !inventory.getStackInSlot(KCORE_SLOT).isEmpty();
    }

    public ItemStack getKCore() {
        return inventory.getStackInSlot(KCORE_SLOT);
    }

    @Override
    public void onBreakBlock() {
        super.onBreakBlock();
        if (level != null && !level.isClientSide) {
            ItemStack stack = inventory.getStackInSlot(KCORE_SLOT);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains(NBTKEY_KCORE)) {
            inventory.deserializeNBT(reg, tag.getCompound(NBTKEY_KCORE));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put(NBTKEY_KCORE, inventory.serializeNBT(reg));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = super.getUpdateTag(reg);
        tag.put(NBTKEY_KCORE, inventory.serializeNBT(reg));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider reg) {
        super.handleUpdateTag(tag, reg);
        if (tag.contains(NBTKEY_KCORE)) {
            inventory.deserializeNBT(reg, tag.getCompound(NBTKEY_KCORE));
        }
    }
}
