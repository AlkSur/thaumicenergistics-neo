package thaumicenergistics.common.integration.neoecoae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.me.cells.BasicCellInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** An essentia matrix as ECO's drives see it: AE2's basic cell inventory plus the ECO tier and accounting. */
final class EssentiaMatrixCell implements IECOStorageCell {

    static final IECOCellHandler HANDLER = new IECOCellHandler() {
        @Override
        public boolean isCell(ItemStack stack) {
            return stack.getItem() instanceof EssentiaMatrixItem;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            if (!(stack.getItem() instanceof EssentiaMatrixItem matrix)) return null;
            BasicCellInventory inventory = BasicCellInventory.createInventory(stack, host);
            return inventory == null ? null : new EssentiaMatrixCell(inventory, matrix);
        }
    };

    private final BasicCellInventory inventory;
    private final EssentiaMatrixItem matrix;

    private EssentiaMatrixCell(BasicCellInventory inventory, EssentiaMatrixItem matrix) {
        this.inventory = inventory;
        this.matrix = matrix;
    }

    @Override public IECOTier getTier() { return matrix.getTier(); }
    @Override public ECOCellType getCellType() { return matrix.getCellType(); }
    @Override public long getStoredItemTypes() { return inventory.getStoredItemTypes(); }
    @Override public long getTotalItemTypes() { return inventory.getTotalItemTypes(); }
    @Override public long getUsedBytes() { return inventory.getUsedBytes(); }
    @Override public long getTotalBytes() { return inventory.getTotalBytes(); }

    @Override public CellState getStatus() { return inventory.getStatus(); }
    @Override public double getIdleDrain() { return inventory.getIdleDrain(); }
    @Override public boolean canFitInsideCell() { return inventory.canFitInsideCell(); }
    @Override public void persist() { inventory.persist(); }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return inventory.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return inventory.extract(what, amount, mode, source);
    }

    @Override public void getAvailableStacks(KeyCounter out) { inventory.getAvailableStacks(out); }
    @Override public boolean isPreferredStorageFor(AEKey what, IActionSource source) { return inventory.isPreferredStorageFor(what, source); }
    @Override public Component getDescription() { return inventory.getDescription(); }
}
