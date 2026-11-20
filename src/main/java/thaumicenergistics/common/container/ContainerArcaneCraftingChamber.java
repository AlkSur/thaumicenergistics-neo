package thaumicenergistics.common.container;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.neoecoae.NeoEcoItems;
import thaumicenergistics.common.tiles.TileArcaneCraftingChamber;
import thaumicenergistics.init.ModItems;

/** Knowledge core slots (9 per row) above the player inventory, plus the chamber's status for the screen. */
public class ContainerArcaneCraftingChamber extends AbstractContainerMenu {

    public static final int DATA_CRAFTING = 0, DATA_BATCH = 1, DATA_CAPACITY = 2, DATA_PROGRESS = 3,
            DATA_SOURCE = 4, DATA_WAIT_ASPECT = 5, DATA_WAIT_HAVE = 6, DATA_WAIT_NEED = 7, DATA_ONLINE = 8;
    public static final int DATA_COUNT = 9;

    @Nullable
    public final TileArcaneCraftingChamber chamber;
    private final Container cores;
    private final int coreSlots;
    public final ContainerData data;

    /** Client side: the chamber is read back from the position the server sent. */
    public ContainerArcaneCraftingChamber(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof TileArcaneCraftingChamber tile
                ? tile : null);
    }

    public ContainerArcaneCraftingChamber(int id, Inventory inventory, @Nullable TileArcaneCraftingChamber chamber) {
        super(NeoEcoItems.CHAMBER_MENU.get(), id);
        this.chamber = chamber;
        this.cores = chamber != null ? chamber.getCores() : new SimpleContainer(9);
        this.coreSlots = cores.getContainerSize();
        this.data = chamber != null && !inventory.player.level().isClientSide()
                ? new ChamberData(chamber) : new SimpleContainerData(DATA_COUNT);
        int rows = rows();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(cores, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(ModItems.KNOWLEDGE_CORE.get());
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }
        int top = playerInventoryTop();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, top + 58));
        }
        addDataSlots(data);
    }

    public int rows() {
        return coreSlots / 9;
    }

    /** Core rows, then a status strip, then the player inventory. */
    public int playerInventoryTop() {
        return 18 + rows() * 18 + 14 + 26;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < coreSlots) {
            if (!moveItemStackTo(stack, coreSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!stack.is(ModItems.KNOWLEDGE_CORE.get()) || !moveItemStackTo(stack, 0, coreSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return chamber != null && !chamber.isRemoved()
                && player.distanceToSqr(chamber.getBlockPos().getCenter()) <= 64.0;
    }

    /** Primals in the order of {@link #DATA_WAIT_ASPECT} (index + 1; 0 = nothing missing). */
    public static final Aspect[] PRIMALS = {Aspect.AIR, Aspect.WATER, Aspect.FIRE, Aspect.ORDER, Aspect.ENTROPY, Aspect.EARTH};

    private static final class ChamberData implements ContainerData {
        private final TileArcaneCraftingChamber tile;

        ChamberData(TileArcaneCraftingChamber tile) {
            this.tile = tile;
        }

        @Override
        public int get(int index) {
            // Container data travels as shorts.
            return Math.min(Short.MAX_VALUE, raw(index));
        }

        private int raw(int index) {
            var wait = tile.visWait();
            return switch (index) {
                case DATA_CRAFTING -> tile.isCrafting() ? 1 : 0;
                case DATA_BATCH -> tile.getBatchSize();
                case DATA_CAPACITY -> tile.getParallelCapacity();
                case DATA_PROGRESS -> Math.round(tile.getCraftProgress() * 100);
                case DATA_SOURCE -> tile.isVisSourceAvailable() ? 1 : 0;
                case DATA_WAIT_ASPECT -> wait == null ? 0 : java.util.Arrays.asList(PRIMALS).indexOf(wait.aspect()) + 1;
                case DATA_WAIT_HAVE -> wait == null ? 0 : wait.haveCvis() / 10;
                case DATA_WAIT_NEED -> wait == null ? 0 : wait.needCvis() / 10;
                case DATA_ONLINE -> tile.isNodeActive() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
