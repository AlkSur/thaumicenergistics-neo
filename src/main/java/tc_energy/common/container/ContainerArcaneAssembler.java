package tc_energy.common.container;

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
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.item.VisDiscountGear;
import dev.tc4port.thaumcraft.api.item.WarpingGear;
import tc_energy.common.container.slot.ReadOnlySlot;
import tc_energy.common.tiles.TileArcaneAssembler;
import tc_energy.init.ModMenuTypes;

/**
 * 濂ユ湳瑁呴厤鍣?Container銆? *
 * 鍧愭爣瀹屽叏瀵瑰簲1.7.10 ThaumicEnergistics锛? *   鐜╁鑳屽寘 Y=115锛屽揩鎹锋爮 Y=173
 *   3脳7 閰嶆柟妲?(26, 25)
 *   鐭ヨ瘑鏍稿績 (187, 8)
 *   4鍔犻€熷崱 (187, 26)
 *   鐩爣浜х墿 (14, 87)
 *   4鎶樻墸鎶ょ敳妲?(210, 26)
 *
 * 涓嶇户鎵?ThEContainerBase锛屽洜涓哄熀绫绘妸鐜╁鑳屽寘鏀惧湪AE2鏍囧噯 Y=169锛? * 鑰屽ゥ鏈閰嶅櫒GUI璐村浘鐨勮儗鍖呭湪鏇撮珮浣嶇疆 Y=115銆? */
public class ContainerArcaneAssembler extends AbstractContainerMenu {

    // ===== 鍧愭爣甯搁噺锛堟柊鐗堣创鍥?256脳256锛氭牸瀛?16脳16銆佹璺?18锛?=====
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

    // ===== 妲戒綅鍒掑垎锛坬uickMoveStack 鐢級 =====
    // 椤哄簭 = 鐜╁(36) 鈫?KCore(1) 鈫?3脳7閰嶆柟(21) 鈫?鍗囩骇鍗?4) 鈫?鐩爣(1) 鈫?鎶ょ敳(4) = 67
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

    /** 渚?GUI 娓叉煋绌烘Ы鍥炬爣锛堝姞閫熷崱/鎶ょ敳锛夋椂瀹氫綅妲戒綅銆?*/
    public Slot getUpgradeSlot(int i) { return slots.get(UPGRADE_START + i); }
    public Slot getArmorSlot(int i) { return slots.get(ARMOR_START + i); }
    public Slot getKCoreSlot() { return slots.get(KCORE_IDX); }

    // ===== 閰嶆柟棰勮锛堝鎴风锛岀敱 AssemblerPreviewSyncPacket 鏇存柊锛?=====
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

    /** 鍙鏁版嵁鍚屾锛氭湇鍔＄璇?tile锛屽鎴风瀛樺悓姝ュ€硷紙GUI 杩涘害鏉?婧愯川鏉′笉渚濊禆 BE 缃戠粶鍚屾锛岄潬瀹瑰櫒姣?tick 鍚屾锛?*/
    public final ContainerData visData;

    /** 绱㈠紩绾﹀畾 */
    public static final int DATA_AIR = 0, DATA_WATER = 1, DATA_FIRE = 2, DATA_ORDER = 3,
            DATA_ENTROPY = 4, DATA_EARTH = 5, DATA_CRAFTING = 6, DATA_CRAFT_TICK = 7, DATA_TICKS_PER_CRAFT = 8;
    public static final int DATA_COUNT = 9;

    /** 鏈嶅姟绔細姣忔 get 閮戒粠 tile 璇诲疄鏃跺€硷紙broadcastChanges 姣?tick 璋冪敤锛?*/
    private static class TileVisData implements ContainerData {
        private final TileArcaneAssembler tile;
        TileVisData(TileArcaneAssembler tile) { this.tile = tile; }
        /** 鑺傛祦锛氭瘡 4 鍗曚綅鎵嶅彉涓€娆★紙broadcastChanges 鍙彂鍙樺寲鐨勫€?鈫?鍙戝寘棰戠巼闄?4 鍊嶏紝杩涘害鏉′粛娴佺晠锛?*/
        private static int throttle(int v) { return (v / 4) * 4; }
        @Override public int get(int index) {
            if (tile == null || tile.getStoredVis() == null) return 0;
            return switch (index) {
                case DATA_AIR -> throttle(tile.getStoredVis().getOrDefault(TileArcaneAssembler.PRIMALS[0], 0));
                case DATA_WATER -> throttle(tile.getStoredVis().getOrDefault(TileArcaneAssembler.PRIMALS[2], 0));
                case DATA_FIRE -> throttle(tile.getStoredVis().getOrDefault(TileArcaneAssembler.PRIMALS[1], 0));
                case DATA_ORDER -> throttle(tile.getStoredVis().getOrDefault(TileArcaneAssembler.PRIMALS[4], 0));
                case DATA_ENTROPY -> throttle(tile.getStoredVis().getOrDefault(TileArcaneAssembler.PRIMALS[5], 0));
                case DATA_EARTH -> throttle(tile.getStoredVis().getOrDefault(TileArcaneAssembler.PRIMALS[3], 0));
                case DATA_CRAFTING -> tile.isCrafting() ? 1 : 0;
                case DATA_CRAFT_TICK -> throttle(tile.getCraftTickCounter());
                case DATA_TICKS_PER_CRAFT -> tile.getTicksPerCraft();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { /* 鏈嶅姟绔彧璇?*/ }
        @Override public int getCount() { return DATA_COUNT; }
    }

    /** 瀹㈡埛绔細淇濆瓨鏈嶅姟绔悓姝ユ潵鐨勫€?*/
    private static class ClientVisData implements ContainerData {
        private final int[] values = new int[DATA_COUNT];
        @Override public int get(int index) { return index >= 0 && index < DATA_COUNT ? values[index] : 0; }
        @Override public void set(int index, int value) { if (index >= 0 && index < DATA_COUNT) values[index] = value; }
        @Override public int getCount() { return DATA_COUNT; }
    }

    /** 鏈嶅姟绔瀯閫狅紙鐢?Block#getMenuProvider 璋冪敤锛屼笉甯?tile锛?*/
    public ContainerArcaneAssembler(int id, Inventory inv) {
        this(id, inv, (TileArcaneAssembler) null);
    }

    /** 鏈嶅姟绔瀯閫狅紙甯?tile 寮曠敤锛?*/
    public ContainerArcaneAssembler(int id, Inventory inv, TileArcaneAssembler tile) {
        super(ModMenuTypes.ARCANE_ASSEMBLER.get(), id);
        this.assembler = tile;
        // 鍙鏁版嵁鍚屾锛氭湇鍔＄浠?tile 璇诲疄鏃跺€硷紱瀹㈡埛绔瓨鍚屾鍊硷紙涓嶄緷璧?BE 缃戠粶鍚屾锛?
        if (inv.player.level().isClientSide) {
            this.visData = new ClientVisData();
        } else {
            this.visData = new TileVisData(tile);
        }
        this.addDataSlots(this.visData);

        // ===== 1. 鐜╁鑳屽寘 3琛?=====
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9,
                        PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
        // ===== 2. 蹇嵎鏍?1琛?=====
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, PLAYER_INV_X + c * 18, HOTBAR_Y));

        ItemStackHandler machineHandler = wrapMachineInventory();

        // ===== 3. 鐭ヨ瘑鏍稿績 (Tile.KCORE_SLOT = 0) =====
        addSlot(new SlotItemHandler(machineHandler, TileArcaneAssembler.KCORE_SLOT, KCORE_X, KCORE_Y) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(tc_energy.init.ModItems.KNOWLEDGE_CORE.get());
            }
        });

        // ===== 4. 3脳7 閰嶆柟妲斤紙鍙灞曠ず锛孉E2 寮忛槻鏁寸悊锛?=====
        for (int r = 0; r < PATTERN_ROWS; r++)
            for (int c = 0; c < PATTERN_COLS; c++) {
                int invIdx = TileArcaneAssembler.PATTERN_SLOT_START + r * PATTERN_COLS + c;
                addSlot(new ReadOnlySlot(machineHandler, invIdx,
                        PATTERN_X + c * 18, PATTERN_Y + r * 18));
            }

        // ===== 5. 4寮燗E2閫熷害鍗★紙鎸佷箙鍖栧湪 Tile 妲?27~30锛屽叧 GUI 涓嶄涪澶憋級 =====
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            int invIdx = TileArcaneAssembler.UPGRADE_SLOT_START + i;
            addSlot(new SlotItemHandler(machineHandler, invIdx, UPGRADE_X, UPGRADE_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return AEItems.SPEED_CARD.is(s);
                }
            });
        }

        // ===== 6. 鐩爣浜х墿杈撳嚭妲斤紙鍙灞曠ず锛岀敱 GUI 浠庣綉缁滃寘鏁版嵁娓叉煋锛涙Ы浣嶇Щ鍑哄睆骞曢伩鍏嶅弻娓叉煋锛?=====
        addSlot(new ReadOnlySlot(machineHandler, TileArcaneAssembler.TARGET_SLOT, -2000, -2000));

        // ===== 7. 4涓姢鐢叉姌鎵ｆЫ =====
        for (int i = 0; i < ARMOR_COUNT; i++) {
            final int armorIdx = i;
            int invIdx = TileArcaneAssembler.ARMOR_SLOT_START + i;
            final net.minecraft.world.entity.EquipmentSlot eq = switch (i) {
                case 0 -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                case 1 -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                case 2 -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                default -> net.minecraft.world.entity.EquipmentSlot.FEET;
            };
            // 绌烘Ы鍥炬爣锛氬師鐗堝ご鐩?鑳哥敳/鎶よ吙/闈村瓙锛圔LOCK_ATLAS 鍥鹃泦锛屽悓 WCWT 鎵嬪姩鍚堟垚鍖哄仛娉曪級
            final net.minecraft.resources.ResourceLocation armorIcon = switch (i) {
                case 0 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case 1 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case 2 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                default -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            };
            addSlot(new SlotItemHandler(machineHandler, invIdx, ARMOR_X, ARMOR_Y + i * 18) {
                @Override public boolean mayPlace(ItemStack s) {
                    if (s.getItem() instanceof VisDiscountGear) return true;
                    if (s.getItem() instanceof WarpingGear) return true;
                    if (s.getItem() instanceof net.minecraft.world.item.Equipable eqItem)
                        return eqItem.getEquipmentSlot() == eq;
                    return false;
                }
                @Override
                public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                    return com.mojang.datafixers.util.Pair.of(
                            net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, armorIcon);
                }
                @Override public void setChanged() {
                    super.setChanged();
                    if (assembler != null)
                        assembler.onInventoryChanged(TileArcaneAssembler.ARMOR_SLOT_START + armorIdx);
                }
            });
        }
    }

    /** 瀹㈡埛绔瀯閫狅紙IMenuTypeExtension 鍙嶅皠璋冪敤锛?*/
    public ContainerArcaneAssembler(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, resolveTile(inv, buf));
    }

    private static TileArcaneAssembler resolveTile(Inventory inv, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        return (TileArcaneAssembler) inv.player.level().getBlockEntity(pos);
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

    // ===== Shift+鐐瑰嚮 =====
    @Override public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < PLAYER_END) {
            // 鐜╁鑳屽寘 鈫?鐭ヨ瘑鏍稿績 鈫?鎶ょ敳 鈫?閫熷害鍗?鈫?鑳屽寘鈫斿揩鎹锋爮
            boolean merged = false;

            if (stack.is(tc_energy.init.ModItems.KNOWLEDGE_CORE.get())) {
                merged = moveItemStackTo(stack, KCORE_IDX, KCORE_IDX + 1, false);
            }

            if (!merged && (stack.getItem() instanceof VisDiscountGear
                    || stack.getItem() instanceof WarpingGear
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
            // 鏈哄櫒妲?鈫?鐜╁鑳屽寘
            if (!moveItemStackTo(stack, 0, PLAYER_END, true)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }

    @Override public boolean stillValid(Player player) { return true; }
}
