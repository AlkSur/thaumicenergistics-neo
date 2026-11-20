package thaumicenergistics.common.container.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import appeng.util.Platform;
import appeng.util.inv.CarriedItemInventory;
import appeng.util.inv.PlayerInternalInventory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.common.items.TCFunctionalItems;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;

/**
 * 奥术合成结果槽：继承 AE2 CraftingTermSlot 使客户端识别（shift/右键/space 合成自动生效），
 * 并保留 vis 消耗检查（mayPickup）与取走时 vis 扣除 + 自动补货（onTake）。
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    /** TC4 内部使用 centivis，1 vis = 100 centivis */
    private static final int CENTIVIS = 100;

    private ContainerArcaneCraftingTerminal container = null;
    private final MEStorage storage;
    private final IEnergySource energySrc;
    private final IActionSource mySrc;
    private final InternalInventory craftingGrid;

    public ArcaneCraftingResultSlot(Player player, ContainerArcaneCraftingTerminal container,
                                    InternalInventory craftingGridInv, InternalInventory secondMatrix,
                                    MEStorage storage, IEnergySource energySrc, IActionSource mySrc) {
        super(player, mySrc, energySrc, storage, craftingGridInv, secondMatrix, (ICraftingGridMenu) container);
        this.container = container;
        this.storage = storage;
        this.energySrc = energySrc;
        this.mySrc = mySrc;
        this.craftingGrid = craftingGridInv;
    }

    @Override
    public boolean mayPickup(Player player) {
        // AE2 原版：结果槽不可 vanilla 点按取走，所有取走走 doClick（含 vis 检查）
        return false;
    }

    @Override
    public void doClick(InventoryAction action, Player who) {
        // Arcane recipes never go through AE2's craftItem (its findRecipe only knows vanilla recipes).
        if (container != null && container.hasValidRecipe) {
            doArcaneCraft(action, who);
            return;
        }
        super.doClick(action, who);
    }

    /**
     * Crafts an arcane recipe the way TC's arcane workbench settles it: everything that can fail is
     * checked before the wand pays, each slot gives up one item and then takes back the recipe's
     * remainder (empty bucket, retained primordial pearl), and the wand is persisted after paying.
     */
    private void doArcaneCraft(InventoryAction action, Player who) {
        if (this.getItem().isEmpty() || isRemote()) {
            return;
        }
        var grid = craftingGrid;
        var level = who.level();
        if (level.isClientSide()) return;

        IArcaneRecipe recipe = ThaumcraftCraftingManager.findMatchingArcaneRecipeEntry(grid.toContainer(), who);
        if (recipe == null) {
            return;
        }

        InternalInventory target;
        int maxTimesToCraft;
        if (action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL) {
            target = new PlayerInternalInventory(who.getInventory());
            ItemStack first = recipe.getCraftingResult(grid.toContainer());
            if (first.isEmpty()) return;
            maxTimesToCraft = Math.max(1, first.getMaxStackSize() / first.getCount());
            if (action == InventoryAction.CRAFT_ALL) {
                maxTimesToCraft *= Inventory.INVENTORY_SIZE;
            }
        } else if (action == InventoryAction.CRAFT_STACK) {
            // AE2's "craft a stack into the hand" (TEA2-13): repeat until the carried stack is full.
            target = new CarriedItemInventory(getMenu());
            ItemStack first = recipe.getCraftingResult(grid.toContainer());
            if (first.isEmpty()) return;
            maxTimesToCraft = Math.max(1, first.getMaxStackSize() / first.getCount());
        } else {
            target = new CarriedItemInventory(getMenu());
            maxTimesToCraft = 1;
        }

        boolean wandPaid = false;
        for (int x = 0; x < maxTimesToCraft; x++) {
            var craftContainer = grid.toContainer();
            // Research was checked once above; later rounds only re-check the grid.
            if (x > 0 && !recipe.matches(craftContainer, level, who)) break;
            ItemStack result = recipe.getCraftingResult(craftContainer);
            if (result.isEmpty() || !target.simulateAdd(result).isEmpty()) break;

            AspectList centivisCost = centivisCost(recipe, craftContainer);
            TCFunctionalItems.WandCastingItem wand = null;
            ItemStack wandStack = ItemStack.EMPTY;
            if (!centivisCost.isEmpty()) {
                wandStack = container.liveWand();
                if (!(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem w)
                        || !thaumcraft.common.blockentities.ArcaneWorkbenchBlockEntity.isValidWand(wandStack)
                        || !w.consumeVisCost(wandStack, who, centivisCost, false, true)) {
                    break;
                }
                wand = w;
            }

            NonNullList<ItemStack> remainders = recipe.getRemainingItems(craftContainer);
            if (wand != null) {
                if (!wand.consumeVisCost(wandStack, who, centivisCost, true, true)) break;
                wandPaid = true;
            }

            var beforeItems = new HashMap<Integer, ItemStack>();
            for (int i = 0; i < grid.size(); i++) {
                var s = grid.getStackInSlot(i);
                if (!s.isEmpty()) beforeItems.put(i, s.copy());
            }
            for (int i = 0; i < grid.size(); i++) {
                if (!grid.getStackInSlot(i).isEmpty()) {
                    grid.extractItem(i, 1, false);
                }
                returnRemainder(grid, i, i < remainders.size() ? remainders.get(i) : ItemStack.EMPTY, who);
            }
            refillFromNetwork(grid, beforeItems);

            result.onCraftedBy(level, who, result.getCount());
            net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(who, result, craftContainer);
            var extra = target.addItems(result.copy());
            if (!extra.isEmpty()) {
                Platform.spawnDrops(level, who.blockPosition(), List.of(extra));
                break;
            }
        }

        if (wandPaid) {
            container.persistWand();
        }
        container.slotsChanged(grid.toContainer());
    }

    private static AspectList centivisCost(IArcaneRecipe recipe, net.minecraft.world.Container craftContainer) {
        AspectList aspects = recipe.getAspects();
        if (aspects == null || aspects.isEmpty()) aspects = recipe.getAspects(craftContainer);
        AspectList centivis = new AspectList();
        if (aspects == null) return centivis;
        for (Aspect aspect : aspects.aspects()) {
            centivis.add(aspect, aspects.amount(aspect) * CENTIVIS);
        }
        return centivis;
    }

    /** TC ArcaneWorkbenchMenu.consumeCraftingGrid: remainder back into the slot, else to the player. */
    private static void returnRemainder(InternalInventory grid, int slot, ItemStack remainder, Player who) {
        if (remainder.isEmpty()) return;
        ItemStack current = grid.getStackInSlot(slot);
        if (current.isEmpty()) {
            grid.setItemDirect(slot, remainder.copy());
        } else if (ItemStack.isSameItemSameComponents(current, remainder)
                && current.getCount() + remainder.getCount() <= current.getMaxStackSize()) {
            grid.setItemDirect(slot, current.copyWithCount(current.getCount() + remainder.getCount()));
        } else if (!who.getInventory().add(remainder.copy())) {
            who.drop(remainder.copy(), false);
        }
    }

    /** Top a slot back up from the network, but only with the item that was there before. */
    private void refillFromNetwork(InternalInventory grid, Map<Integer, ItemStack> beforeItems) {
        if (storage == null || energySrc == null || mySrc == null) return;
        for (var entry : beforeItems.entrySet()) {
            int slot = entry.getKey();
            var beforeItem = entry.getValue();
            var currentItem = grid.getStackInSlot(slot);
            if (!currentItem.isEmpty() && !ItemStack.isSameItemSameComponents(currentItem, beforeItem)) continue;
            if (currentItem.getCount() >= beforeItem.getCount()) continue;
            var key = AEItemKey.of(beforeItem);
            if (key == null) continue;
            long extracted = StorageHelper.poweredExtraction(
                    energySrc, storage, key, beforeItem.getCount() - currentItem.getCount(), mySrc);
            if (extracted > 0) {
                grid.setItemDirect(slot, beforeItem.copyWithCount((int) (currentItem.getCount() + extracted)));
            }
        }
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        if (container != null) {
            container.setCraftingResultBeingTaken(true);
        }
        try {
            // 记录消耗前各合成格物品，取走后从 ME 存储自动补回（AE2 终端行为）
            Map<Integer, ItemStack> beforeItems = new HashMap<>();
            var grid = craftingGrid;
            for (int i = 0; i < grid.size(); i++) {
                var s = grid.getStackInSlot(i);
                if (!s.isEmpty()) beforeItems.put(i, s.copy());
            }

            super.onTake(player, stack);

            // 自动补货：合成格材料被消耗后从 ME 存储补回
            if (!beforeItems.isEmpty()) {
                if (storage != null && energySrc != null && mySrc != null) {
                    for (var entry : beforeItems.entrySet()) {
                        int slot = entry.getKey();
                        var beforeItem = entry.getValue();
                        var currentItem = grid.getStackInSlot(slot);
                        if (currentItem.getCount() < beforeItem.getCount()) {
                            long needed = beforeItem.getCount() - currentItem.getCount();
                            var key = AEItemKey.of(beforeItem);
                            if (key != null) {
                                long extracted = StorageHelper.poweredExtraction(
                                        energySrc, storage, key, needed, mySrc);
                                if (extracted > 0) {
                                    grid.setItemDirect(slot, beforeItem.copyWithCount(
                                            (int) (currentItem.getCount() + extracted)));
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            if (container != null) {
                container.setCraftingResultBeingTaken(false);
            }
        }
    }

}
