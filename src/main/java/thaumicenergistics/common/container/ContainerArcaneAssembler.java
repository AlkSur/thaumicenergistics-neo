package thaumicenergistics.common.container;

import java.util.List;

import appeng.core.definitions.AEItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumcraft.api.IVisDiscountGear;
import thaumcraft.api.IWarpingGear;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.container.slot.ReadOnlySlot;
import thaumicenergistics.common.tiles.TileArcaneAssembler;
import thaumicenergistics.init.ModMenuTypes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import thaumicenergistics.init.ModItems;

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

    // ===== 坐标常量（新版贴图 256×256：格子 16×16、步距 18） =====
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 147;
    private static final int HOTBAR_Y     = 205;

    private static final int PATTERN_X = 26;
    private static final int PATTERN_Y = 15;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COLS = 7;

    private static final int KCORE_X   = 175;
    private static final int KCORE_Y   = 6;

    private static final int UPGRADE_X = 175;
    private static final int UPGRADE_Y = 24;
    private static final int UPGRADE_COUNT = 4;

    private static final int TARGET_X  = 114;
    private static final int TARGET_Y  = 97;

    private static final int ARMOR_X   = 152;
    private static final int ARMOR_Y   = 73;
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

    public final TileArcaneAssembler assembler;

    /** 供 GUI 渲染空槽图标（加速卡/护甲）时定位槽位。 */
    public Slot getUpgradeSlot(int i) { return slots.get(UPGRADE_START + i); }
    public Slot getArmorSlot(int i) { return slots.get(ARMOR_START + i); }
    public Slot getKCoreSlot() { return slots.get(KCORE_IDX); }

    // ===== 配方预览（客户端，由 AssemblerPreviewSyncPacket 更新） =====
    public boolean clientPreviewActive = false;
    public ItemStack[] clientPreviewGrid = new ItemStack[9];
    public ItemStack clientPreviewResult = ItemStack.EMPTY;

    public void applyClientPreview(List<ItemStack> grid, ItemStack result, boolean active) {
        this.clientPreviewActive = active;
        this.clientPreviewGrid = new ItemStack[9];
        if (grid != null) {
            for (int i = 0; i < 9 && i < grid.size(); i++) {
                this.clientPreviewGrid[i] = grid.get(i) != null ? grid.get(i).copy() : ItemStack.EMPTY;
            }
        }
        this.clientPreviewResult = result != null ? result.copy() : ItemStack.EMPTY;
    }

    /** 可视数据同步：服务端读 tile，客户端存同步值（GUI 进度条/源质条不依赖 BE 网络同步，靠容器每 tick 同步） */
    public final ContainerData visData;

    /** 索引约定 */
    public static final int DATA_AIR = 0, DATA_WATER = 1, DATA_FIRE = 2, DATA_ORDER = 3,
            DATA_ENTROPY = 4, DATA_EARTH = 5, DATA_CRAFTING = 6, DATA_CRAFT_TICK = 7, DATA_TICKS_PER_CRAFT = 8;
    public static final int DATA_COUNT = 9;

    /** 服务端：每次 get 都从 tile 读实时值（broadcastChanges 每 tick 调用） */
    private static class TileVisData implements ContainerData {
        private final TileArcaneAssembler tile;
        TileVisData(TileArcaneAssembler tile) { this.tile = tile; }
        /** 节流：每 4 单位才变一次（broadcastChanges 只发变化的值 → 发包频率降 4 倍，进度条仍流畅） */
        private static int throttle(int v) { return (v / 4) * 4; }
        @Override public int get(int index) {
            if (tile == null || tile.getStoredVis() == null) return 0;
            return switch (index) {
                case DATA_AIR -> throttle(tile.getStoredVis().getAmount(Aspect.AIR));
                case DATA_WATER -> throttle(tile.getStoredVis().getAmount(Aspect.WATER));
                case DATA_FIRE -> throttle(tile.getStoredVis().getAmount(Aspect.FIRE));
                case DATA_ORDER -> throttle(tile.getStoredVis().getAmount(Aspect.ORDER));
                case DATA_ENTROPY -> throttle(tile.getStoredVis().getAmount(Aspect.ENTROPY));
                case DATA_EARTH -> throttle(tile.getStoredVis().getAmount(Aspect.EARTH));
                case DATA_CRAFTING -> tile.isCrafting() ? 1 : 0;
                case DATA_CRAFT_TICK -> throttle(tile.getCraftTickCounter());
                case DATA_TICKS_PER_CRAFT -> tile.getTicksPerCraft();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { /* 服务端只读 */ }
        @Override public int getCount() { return DATA_COUNT; }
    }

    /** 客户端：保存服务端同步来的值 */
    private static class ClientVisData implements ContainerData {
        private final int[] values = new int[DATA_COUNT];
        @Override public int get(int index) { return index >= 0 && index < DATA_COUNT ? values[index] : 0; }
        @Override public void set(int index, int value) { if (index >= 0 && index < DATA_COUNT) values[index] = value; }
        @Override public int getCount() { return DATA_COUNT; }
    }

    /** 服务端构造（由 Block#getMenuProvider 调用，不带 tile） */
    public ContainerArcaneAssembler(int id, Inventory inv) {
        this(id, inv, (TileArcaneAssembler) null);
    }

    /** 服务端构造（带 tile 引用） */
    public ContainerArcaneAssembler(int id, Inventory inv, TileArcaneAssembler tile) {
        super(ModMenuTypes.ARCANE_ASSEMBLER.get(), id);
        this.assembler = tile;
        // 可视数据同步：服务端从 tile 读实时值；客户端存同步值（不依赖 BE 网络同步）
        if (inv.player.level().isClientSide) {
            this.visData = new ClientVisData();
        } else {
            this.visData = new TileVisData(tile);
        }
        this.addDataSlots(this.visData);

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
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }
        });

        // ===== 4. 3×7 配方槽（只读展示，AE2 式防整理） =====
        for (int r = 0; r < PATTERN_ROWS; r++)
            for (int c = 0; c < PATTERN_COLS; c++) {
                int invIdx = TileArcaneAssembler.PATTERN_SLOT_START + r * PATTERN_COLS + c;
                addSlot(new ReadOnlySlot(machineHandler, invIdx,
                        PATTERN_X + c * 18, PATTERN_Y + r * 18));
            }

        // ===== 5. 4张AE2速度卡（持久化在 Tile 槽 27~30，关 GUI 不丢失） =====
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int invIdx = TileArcaneAssembler.UPGRADE_SLOT_START + i;
            addSlot(new SlotItemHandler(machineHandler, invIdx, UPGRADE_X, UPGRADE_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return AEItems.SPEED_CARD.is(s);
                }
            });
        }

        // ===== 6. 目标产物输出槽（只读展示，由 GUI 从网络包数据渲染；槽位移出屏幕避免双渲染） =====
        addSlot(new ReadOnlySlot(machineHandler, TileArcaneAssembler.TARGET_SLOT, -2000, -2000));

        // ===== 7. 4个护甲折扣槽 =====
        for (int i = 0; i < ARMOR_COUNT; i++) {
            final int armorIdx = i;
            int invIdx = TileArcaneAssembler.ARMOR_SLOT_START + i;
            final EquipmentSlot eq = switch (i) {
                case 0 -> EquipmentSlot.HEAD;
                case 1 -> EquipmentSlot.CHEST;
                case 2 -> EquipmentSlot.LEGS;
                default -> EquipmentSlot.FEET;
            };
            // 空槽图标：原版头盔/胸甲/护腿/靴子（BLOCK_ATLAS 图集，同 WCWT 手动合成区做法）
            final ResourceLocation armorIcon = switch (i) {
                case 0 -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case 1 -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case 2 -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                default -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            };
            addSlot(new SlotItemHandler(machineHandler, invIdx, ARMOR_X, ARMOR_Y + i * 18) {
                @Override public boolean mayPlace(ItemStack s) {
                    if (s.getItem() instanceof IVisDiscountGear) return true;
                    if (s.getItem() instanceof IWarpingGear) return true;
                    if (s.getItem() instanceof Equipable eqItem)
                        return eqItem.getEquipmentSlot() == eq;
                    return false;
                }
                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(
                            InventoryMenu.BLOCK_ATLAS, armorIcon);
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
        this(id, inv, resolveTile(inv, buf));
    }

    private static TileArcaneAssembler resolveTile(Inventory inv, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        return (TileArcaneAssembler) inv.player.level().getBlockEntity(pos);
    }

    private ItemStackHandler wrapMachineInventory() {
        final SimpleContainer inv =
                (assembler != null) ? assembler.getInternalInventory()
                        : new SimpleContainer(TileArcaneAssembler.SLOT_COUNT);
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

            if (stack.is(ModItems.KNOWLEDGE_CORE.get())) {
                merged = moveItemStackTo(stack, KCORE_IDX, KCORE_IDX + 1, false);
            }

            if (!merged && (stack.getItem() instanceof IVisDiscountGear
                    || stack.getItem() instanceof IWarpingGear
                    || stack.getItem() instanceof ArmorItem
                    || stack.getItem() instanceof Equipable)) {
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
