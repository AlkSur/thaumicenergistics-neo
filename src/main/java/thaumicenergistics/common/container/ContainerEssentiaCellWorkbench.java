package thaumicenergistics.common.container;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.items.ItemEssentiaCell;
import thaumicenergistics.common.tiles.TileEssentiaCellWorkbench;
import thaumicenergistics.init.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * 源质元件工作台容器：cell 槽绑定到方块实体（持久化），并提供分区编辑操作。
 * 对应 1.7.10 的 {@code ContainerEssentiaCellWorkbench}，分区数据存储在 cell 的 AE2 CellConfig 上。
 */
public class ContainerEssentiaCellWorkbench extends AbstractContainerMenu {

    /** cell 槽编号。 */
    public static final int SLOT_CELL = 0;
    /** 玩家背包起始编号（1..27 背包，28..36 热栏）。 */
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;
    /** 四个升级槽（容器槽位 37..40），右侧保留元件槽。 */
    public static final int UPGRADE_X = 80;
    private static final int UPGRADE_Y = 8;
    private static final int UPGRADE_COUNT = ItemEssentiaCell.UPGRADE_SLOTS;
    private static final int UPGRADE_START = HOTBAR_END;

    private final TileEssentiaCellWorkbench workbench;

    /** 兜底用空库存（workbench 为 null 时）。 */
    private final IItemHandler fallbackCell = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof ItemEssentiaCell;
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    public ContainerEssentiaCellWorkbench(int id, Inventory inv) {
        this(id, inv, (TileEssentiaCellWorkbench) null);
    }

    public ContainerEssentiaCellWorkbench(int id, Inventory inv, TileEssentiaCellWorkbench workbench) {
        super(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), id);
        this.workbench = workbench;

        IItemHandler cellHandler = workbench != null ? workbench.getCellInventory() : fallbackCell;
        addSlot(new SlotItemHandler(cellHandler, 0, 152, 8));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 167 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 225));

        // Bind both sides to slot zero, including the client's synchronized cell stack.
        IItemHandler cellUpgrades = new CellUpgradeHandler(this::currentCellUpgrades);
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            addSlot(new SlotItemHandler(cellUpgrades, i, UPGRADE_X + i * 18, UPGRADE_Y) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack s) {
                    return hasCell() && isCellUpgrade(s) && cellUpgrades.isItemValid(getSlotIndex(), s);
                }

                @Override public boolean isActive() { return hasCell(); }

                @Override
                public void setChanged() {
                    super.setChanged();
                    if (workbench != null) {
                        workbench.setChanged();
                    }
                }
            });
        }
    }

    /** 当前 cell 的升级库存；无 cell 时返回空库存。 */
    private IUpgradeInventory currentCellUpgrades() {
        ItemStack cell = slots.get(SLOT_CELL).getItem();
        if (cell.getItem() instanceof ItemEssentiaCell cellItem) {
            return cellItem.getUpgrades(cell);
        }
        return UpgradeInventories.empty();
    }

    private static boolean isCellUpgrade(ItemStack stack) {
        return AEItems.INVERTER_CARD.is(stack) || AEItems.VOID_CARD.is(stack)
                || AEItems.EQUAL_DISTRIBUTION_CARD.is(stack) || AEItems.FUZZY_CARD.is(stack);
    }

    public Slot getUpgradeSlot(int index) { return slots.get(UPGRADE_START + index); }

    /** 把 AE2 InternalInventory（IUpgradeInventory）适配成 NeoForge 可写句柄；SlotItemHandler.set 强转 Modifiable，缺它则客户端开 GUI 同步即 CCE。 */
    private static final class CellUpgradeHandler implements IItemHandlerModifiable {
        private final java.util.function.Supplier<IUpgradeInventory> supplier;

        CellUpgradeHandler(java.util.function.Supplier<IUpgradeInventory> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            supplier.get().setItemDirect(slot, stack);
        }

        @Override
        public int getSlots() {
            return UPGRADE_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return supplier.get().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var inventory = supplier.get();
            return slot >= 0 && slot < inventory.size() && isCellUpgrade(stack)
                    ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return supplier.get().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            var inventory = supplier.get();
            return slot >= 0 && slot < inventory.size() ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            var inventory = supplier.get();
            return slot >= 0 && slot < inventory.size() && isCellUpgrade(stack)
                    && inventory.isItemValid(slot, stack);
        }
    }

    /** 客户端打开菜单用（IMenuTypeExtension 注册）；workbench 为 null，分区操作走 C2S 到服务端。 */
    public ContainerEssentiaCellWorkbench(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv);
    }

    // ===== 分区操作（由 GUI / 网络调用，服务端执行） =====

    public boolean hasCell() {
        return slots.get(SLOT_CELL).getItem().getItem() instanceof ItemEssentiaCell;
    }

    public List<ResourceLocation> getPartitionAspects() {
        if (workbench == null || !workbench.hasCell()) return List.of();
        return ItemEssentiaCell.getPartitionAspects(workbench.getCell());
    }

    public boolean addAspectToPartition(ResourceLocation aspectId) {
        if (workbench == null || !workbench.hasCell()) return false;
        boolean changed = ItemEssentiaCell.addAspectToPartition(workbench.getCell(), aspectId);
        if (changed) workbench.setChanged();
        return changed;
    }

    public boolean removeAspectFromPartition(ResourceLocation aspectId) {
        if (workbench == null || !workbench.hasCell()) return false;
        boolean changed = ItemEssentiaCell.removeAspectFromPartition(workbench.getCell(), aspectId);
        if (changed) workbench.setChanged();
        return changed;
    }

    public boolean replaceAspectInPartition(ResourceLocation from, ResourceLocation to) {
        if (workbench == null || !workbench.hasCell()) return false;
        boolean changed = ItemEssentiaCell.replaceAspectInPartition(workbench.getCell(), from, to);
        if (changed) workbench.setChanged();
        return changed;
    }

    public void clearPartitioning() {
        if (workbench == null || !workbench.hasCell()) return;
        ItemEssentiaCell.clearPartitioning(workbench.getCell());
        workbench.setChanged();
    }

    public void partitionToContents() {
        if (workbench == null || !workbench.hasCell()) return;
        ItemEssentiaCell.partitionToContents(workbench.getCell());
        workbench.setChanged();
    }

    /**
     * 从物品中提取第一个源质方面（罐子/法杖等 IEssentiaContainerItem）。
     */
    public static ResourceLocation getAspectFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof IEssentiaContainerItem containerItem) {
            AspectList aspects = containerItem.getAspects(stack);
            if (aspects != null && aspects.size() > 0) {
                Aspect first = aspects.getAspects()[0];
                if (first != null) return TCReflection.getAspectId(first);
            }
        }
        return null;
    }

    // ===== 快捷移动 / 生命周期 =====

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (workbench == null || idx < 0 || idx >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (idx == SLOT_CELL) {
            // cell 槽 → 玩家背包
            if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (idx < HOTBAR_END) {
            // 玩家背包 → cell 槽（仅当 cell 槽空且物品是源质元件）
            if (stack.getItem() instanceof ItemEssentiaCell && workbench.getCell().isEmpty()) {
                if (!moveItemStackTo(stack, SLOT_CELL, SLOT_CELL + 1, false)) return ItemStack.EMPTY;
            } else if (isCellUpgrade(stack)) {
                // Supported cards -> the current cell's persistent upgrade inventory.
                if (!moveItemStackTo(stack, UPGRADE_START, UPGRADE_START + UPGRADE_COUNT, false)) return ItemStack.EMPTY;
            } else {
                // 含源质物品 → 尝试添加分区（物品不消费，仅添加分区）
                ResourceLocation aspectId = getAspectFromItem(stack);
                if (aspectId != null && addAspectToPartition(aspectId)) {
                    return original; // 物品留在原槽
                }
                return ItemStack.EMPTY;
            }
        } else {
            // 升级卡槽 → 玩家背包
            if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return workbench == null || workbench.hasLevel() && player.level().getBlockEntity(workbench.getBlockPos()) == workbench;
    }
}
