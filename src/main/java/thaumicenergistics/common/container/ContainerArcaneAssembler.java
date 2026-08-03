package thaumicenergistics.common.container;

import appeng.core.definitions.AEItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumcraft.api.IVisDiscountGear;
import thaumcraft.api.IWarpingGear;
import thaumicenergistics.common.tiles.TileArcaneAssembler;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 奥术装配器 Container。
 *
 * 坐标完全对应1.7.10 ThaumicEnergistics：
 *   玩家背包 Y=115，快捷栏 Y=173
 *   3×7 配方槽 (26, 25)
 *   知识核心 (187, 8)
 *   4加速卡 (187, 26)
 *   目标产物 (14, 87)
 *   4折扣护甲槽 (210, 26)
 *
 * 不继承 ThEContainerBase，因为基类把玩家背包放在AE2标准 Y=169，
 * 而奥术装配器GUI贴图的背包在更高位置 Y=115。
 */
public class ContainerArcaneAssembler extends AbstractContainerMenu {

    // ===== 坐标常量（1:1 对应1.7.10） =====
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 113;
    private static final int HOTBAR_Y     = PLAYER_INV_Y + 58; // 173

    private static final int PATTERN_X = 26;
    private static final int PATTERN_Y = 25;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COLS = 7;

    private static final int KCORE_X   = 187;
    private static final int KCORE_Y   = 8;

    private static final int UPGRADE_X = 187;
    private static final int UPGRADE_Y = 26;
    private static final int UPGRADE_COUNT = 4;

    private static final int TARGET_X  = 14;
    private static final int TARGET_Y  = 87;

    private static final int ARMOR_X   = 210;
    private static final int ARMOR_Y   = 26;
    private static final int ARMOR_COUNT = 4;

    // ===== 槽位划分（quickMoveStack 用） =====
    // 顺序 = 玩家(36) → KCore(1) → 3×7配方(21) → 升级卡(4) → 目标(1) → 护甲(4) = 67
    private static final int PLAYER_END    = 36;
    private static final int KCORE_IDX     = 36;
    private static final int PATTERN_START = 37;
    private static final int PATTERN_END   = PATTERN_START + PATTERN_ROWS * PATTERN_COLS; // 58
    private static final int UPGRADE_START = PATTERN_END;                                    // 58
    private static final int UPGRADE_END   = UPGRADE_START + UPGRADE_COUNT;                  // 62
    private static final int TARGET_IDX    = UPGRADE_END;                                    // 62
    private static final int ARMOR_START   = TARGET_IDX + 1;                                 // 63
    private static final int ARMOR_END     = ARMOR_START + ARMOR_COUNT;                      // 67

    /** 升级卡 handler（独立4格，与 Tile 的 speedUpgrades 字段同步） */
    final ItemStackHandler upgradeHandler = new ItemStackHandler(UPGRADE_COUNT) {
        @Override protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            if (assembler != null) {
                int count = 0;
                for (int i = 0; i < UPGRADE_COUNT; i++)
                    if (!getStackInSlot(i).isEmpty()) count++;
                assembler.setSpeedUpgrades(count);
            }
        }
    };

    public final TileArcaneAssembler assembler;

    /** 服务端构造（由 Block#getMenuProvider 调用，不带 tile） */
    public ContainerArcaneAssembler(int id, Inventory inv) {
        this(id, inv, (TileArcaneAssembler) null);
    }

    /** 服务端构造（带 tile 引用） */
    public ContainerArcaneAssembler(int id, Inventory inv, TileArcaneAssembler tile) {
        super(ModMenuTypes.ARCANE_ASSEMBLER.get(), id);
        this.assembler = tile;

        // ===== 1. 玩家背包 3行 =====
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9,
                        PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
        // ===== 2. 快捷栏 1行 =====
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, PLAYER_INV_X + c * 18, HOTBAR_Y));

        ItemStackHandler machineHandler = wrapMachineInventory();

        // ===== 3. 知识核心 (Tile.KCORE_SLOT = 0) =====
        addSlot(new SlotItemHandler(machineHandler, TileArcaneAssembler.KCORE_SLOT, KCORE_X, KCORE_Y) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(thaumicenergistics.init.ModItems.KNOWLEDGE_CORE.get());
            }
        });

        // ===== 4. 3×7 配方槽（只读展示） =====
        for (int r = 0; r < PATTERN_ROWS; r++)
            for (int c = 0; c < PATTERN_COLS; c++) {
                int invIdx = TileArcaneAssembler.PATTERN_SLOT_START + r * PATTERN_COLS + c;
                addSlot(new SlotItemHandler(machineHandler, invIdx,
                        PATTERN_X + c * 18, PATTERN_Y + r * 18) {
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public int getMaxStackSize() { return 1; }
                });
            }

        // ===== 5. 4张AE2速度卡 =====
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            addSlot(new SlotItemHandler(upgradeHandler, i, UPGRADE_X, UPGRADE_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return AEItems.SPEED_CARD.is(s);
                }
            });
        }

        // ===== 6. 目标产物输出槽（只读展示） =====
        addSlot(new SlotItemHandler(machineHandler, TileArcaneAssembler.TARGET_SLOT, TARGET_X, TARGET_Y) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public boolean mayPickup(Player p) { return false; }
        });

        // ===== 7. 4个护甲折扣槽 =====
        for (int i = 0; i < ARMOR_COUNT; i++) {
            final int armorIdx = i;
            int invIdx = TileArcaneAssembler.ARMOR_SLOT_START + i;
            final net.minecraft.world.entity.EquipmentSlot eq = switch (i) {
                case 0 -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                case 1 -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                case 2 -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                default -> net.minecraft.world.entity.EquipmentSlot.FEET;
            };
            addSlot(new SlotItemHandler(machineHandler, invIdx, ARMOR_X, ARMOR_Y + i * 18) {
                @Override public boolean mayPlace(ItemStack s) {
                    if (s.getItem() instanceof IVisDiscountGear) return true;
                    if (s.getItem() instanceof IWarpingGear) return true;
                    if (s.getItem() instanceof net.minecraft.world.item.Equipable eqItem
                            && eqItem.getEquipmentSlot() == eq) return true;
                    return s.canEquip(eq, null);
                }
                @Override public void setChanged() {
                    super.setChanged();
                    if (assembler != null)
                        assembler.onInventoryChanged(TileArcaneAssembler.ARMOR_SLOT_START + armorIdx);
                }
            });
        }
    }

    /** 客户端构造（IMenuTypeExtension 反射调用） */
    public ContainerArcaneAssembler(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv);
    }

    private ItemStackHandler wrapMachineInventory() {
        final net.minecraft.world.SimpleContainer inv =
                (assembler != null) ? assembler.getInternalInventory()
                        : new net.minecraft.world.SimpleContainer(TileArcaneAssembler.SLOT_COUNT);
        return new ItemStackHandler(inv.getContainerSize()) {
            @Override public int getSlots() { return inv.getContainerSize(); }
            @Override public ItemStack getStackInSlot(int slot) { return inv.getItem(slot); }
            @Override public void setStackInSlot(int slot, ItemStack stack) { inv.setItem(slot, stack); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) return ItemStack.EMPTY;
                ItemStack cur = inv.getItem(slot);
                if (!cur.isEmpty()) return stack;
                if (!simulate) {
                    ItemStack cp = stack.copy(); cp.setCount(1);
                    inv.setItem(slot, cp);
                }
                ItemStack leftover = stack.copy();
                leftover.shrink(1);
                return leftover;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack cur = inv.getItem(slot);
                if (cur.isEmpty()) return ItemStack.EMPTY;
                int take = Math.min(amount, cur.getCount());
                ItemStack res = cur.copy(); res.setCount(take);
                if (!simulate) {
                    ItemStack left = cur.copy(); left.shrink(take);
                    inv.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
                }
                return res;
            }
            @Override public int getSlotLimit(int slot) { return 1; }
        };
    }

    // ===== Shift+点击 =====
    @Override public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < PLAYER_END) {
            // 玩家背包 → 知识核心 → 护甲 → 速度卡 → 背包↔快捷栏
            boolean merged = false;

            if (stack.is(thaumicenergistics.init.ModItems.KNOWLEDGE_CORE.get())) {
                merged = moveItemStackTo(stack, KCORE_IDX, KCORE_IDX + 1, false);
            }

            if (!merged && (stack.getItem() instanceof IVisDiscountGear
                    || stack.getItem() instanceof IWarpingGear
                    || stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                    || stack.getItem() instanceof net.minecraft.world.item.Equipable)) {
                for (int a = 0; a < ARMOR_COUNT && !merged; a++) {
                    Slot armorSlot = slots.get(ARMOR_START + a);
                    if (armorSlot.mayPlace(stack) && !armorSlot.hasItem()) {
                        ItemStack one = stack.copy(); one.setCount(1);
                        armorSlot.set(one);
                        stack.shrink(1);
                        merged = true;
                    }
                }
            }

            if (!merged && AEItems.SPEED_CARD.is(stack)) {
                merged = moveItemStackTo(stack, UPGRADE_START, UPGRADE_END, false);
            }

            if (!merged) {
                if (index < 27) merged = moveItemStackTo(stack, 27, PLAYER_END, false);
                else            merged = moveItemStackTo(stack, 0, 27, false);
            }
            if (!merged) return ItemStack.EMPTY;

        } else {
            // 机器槽 → 玩家背包
            if (!moveItemStackTo(stack, 0, PLAYER_END, true)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }

    @Override public boolean stillValid(Player player) { return true; }
}
