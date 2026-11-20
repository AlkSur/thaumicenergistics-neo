package thaumicenergistics.common.items;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;

/**
 * Creative essentia cell: every aspect in its partition is available without limit and anything of
 * those aspects inserted into it is destroyed, like AE2's creative cell. It used to be an empty
 * oversized cell (TEC-11). Partition it in the essentia cell workbench or AE2's cell workbench.
 */
public class ItemCreativeEssentiaCell extends Item implements ICellWorkbenchItem {

    public ItemCreativeEssentiaCell(Item.Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(AEssentiaKeyType.INSTANCE), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.creative_essentia_cell"));
        var config = getConfigInventory(stack);
        for (int i = 0; i < config.size(); i++) {
            if (config.getKey(i) instanceof AEssentiaKey key) tooltip.add(key.getDisplayName());
        }
    }

    /** Claims creative essentia cells for AE2's storage. Registered in common setup. */
    public static final ICellHandler HANDLER = new ICellHandler() {
        @Override
        public boolean isCell(ItemStack is) {
            return is.getItem() instanceof ItemCreativeEssentiaCell;
        }

        @Nullable
        @Override
        public StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return is.getItem() instanceof ItemCreativeEssentiaCell cell ? new Inventory(cell, is) : null;
        }
    };

    private static final class Inventory implements StorageCell {
        private final Set<AEKey> configured = new LinkedHashSet<>();
        private final ItemStack stack;

        Inventory(ItemCreativeEssentiaCell cell, ItemStack stack) {
            this.stack = stack;
            var config = cell.getConfigInventory(stack);
            for (int i = 0; i < config.size(); i++) {
                if (config.getKey(i) instanceof AEssentiaKey key) configured.add(key);
            }
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return configured.contains(what) ? amount : 0;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return configured.contains(what) ? amount : 0;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var key : configured) out.add(key, Integer.MAX_VALUE);
        }

        @Override
        public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
            return configured.contains(what);
        }

        @Override
        public CellState getStatus() {
            return configured.isEmpty() ? CellState.EMPTY : CellState.TYPES_FULL;
        }

        @Override
        public double getIdleDrain() {
            return 0;
        }

        @Override
        public boolean canFitInsideCell() {
            return false;
        }

        @Override
        public Component getDescription() {
            return stack.getHoverName();
        }

        @Override
        public void persist() {
        }
    }
}
