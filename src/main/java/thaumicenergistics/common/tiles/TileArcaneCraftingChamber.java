package thaumicenergistics.common.tiles;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.config.ThEConfig;
import thaumicenergistics.init.ModItems;

/**
 * Arcane Crafting Chamber (Neo ECO AE linkage): an arcane assembler built for ECO crafting CPUs. It offers the
 * recipes of up to 9/18/27 knowledge cores, draws vis from the network's Vis Relay Interfaces like the
 * assembler, and takes a whole batch of identical crafts at once (32/128/512 by tier, configurable), paying vis
 * for every craft. It has no armor or acceleration card slots. This class names no ECO type.
 */
public class TileArcaneCraftingChamber extends TileArcaneAssembler {

    private final int tier;
    private final SimpleContainer cores;
    private boolean loadingCores;

    public TileArcaneCraftingChamber(BlockEntityType<?> type, BlockPos pos, BlockState state, int tier, ItemLike visual) {
        super(type, pos, state, visual);
        this.tier = tier;
        this.cores = new SimpleContainer(coreSlots(tier)) {
            @Override
            public void setItem(int slot, ItemStack stack) {
                super.setItem(slot, stack);
                if (loadingCores) return;
                TileArcaneCraftingChamber.this.setChanged();
                onKnowledgeCoresChanged();
            }

            @Override
            public ItemStack removeItem(int slot, int amount) {
                ItemStack taken = super.removeItem(slot, amount);
                if (!taken.isEmpty() && !loadingCores) onKnowledgeCoresChanged();
                return taken;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }
        };
    }

    /** Knowledge core slots of a chamber of ECO tier 4, 6 or 9. */
    public static int coreSlots(int tier) {
        return tier >= 9 ? 27 : tier >= 6 ? 18 : 9;
    }

    public int getTier() {
        return tier;
    }

    /** Crafts in the running batch; 0 when idle. */
    public int getBatchSize() {
        return jobCount();
    }

    /** Batch size offered when idle and online. */
    public int getParallelCapacity() {
        return parallelCapacity();
    }

    public SimpleContainer getCores() {
        return cores;
    }

    @Override
    protected List<ItemStack> knowledgeCores() {
        List<ItemStack> list = new ArrayList<>(cores.getContainerSize());
        for (int i = 0; i < cores.getContainerSize(); i++) list.add(cores.getItem(i));
        return list;
    }

    @Override
    protected int parallelCapacity() {
        return ThEConfig.chamberParallel(tier);
    }

    /** A modest idle cache; a running batch raises the limit to what it needs. */
    @Override
    protected int maxStoredCvis() {
        return super.maxStoredCvis() * (tier >= 9 ? 8 : tier >= 6 ? 4 : 2);
    }

    /** Draws vis fast enough to pay for a full batch. */
    @Override
    protected int maxCvisPerReplenish() {
        return super.maxCvisPerReplenish() * Math.max(1, parallelCapacity() / 4);
    }

    @Override
    protected int ticksPerCraft() {
        return tier >= 9 ? 4 : tier >= 6 ? 6 : 10;
    }

    /** The assembler's power for every craft in the batch. */
    @Override
    protected double activePowerPerTick() {
        return ThEConfig.assemblerActivePower() * Math.max(1, jobCount());
    }

    @Override
    public void onBreakBlock() {
        super.onBreakBlock();
        if (level != null && !level.isClientSide) {
            for (int i = 0; i < cores.getContainerSize(); i++) {
                ItemStack stack = cores.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ListTag list = new ListTag();
        for (int i = 0; i < cores.getContainerSize(); i++) {
            ItemStack stack = cores.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Item", stack.save(reg));
            list.add(entry);
        }
        tag.put("Cores", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        loadingCores = true;
        try {
            cores.clearContent();
            for (Tag raw : tag.getList("Cores", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                int slot = entry.getInt("Slot");
                if (slot >= 0 && slot < cores.getContainerSize()) {
                    cores.setItem(slot, ItemStack.parseOptional(reg, entry.getCompound("Item")));
                }
            }
        } finally {
            loadingCores = false;
        }
        super.loadAdditional(tag, reg);
    }
}
