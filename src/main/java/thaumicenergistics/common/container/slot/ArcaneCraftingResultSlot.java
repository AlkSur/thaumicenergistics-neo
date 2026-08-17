package thaumicenergistics.common.container.slot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import appeng.util.inv.PlayerInternalInventory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.TCFunctionalItems;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;

/**
 * 奥术合成结果槽：继承 AE2 CraftingTermSlot 使客户端识别（shift/右键/space 合成自动生效），
 * 并保留 vis 消耗检查（mayPickup）与取走时 vis 扣除 + 自动补货（onTake）。
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    /** TC4 内部使用 centivis，1 vis = 100 centivis */
    private static final int CENTIVIS = 100;

    private AspectList requiredAspects = null;
    private ItemStack wandStack = ItemStack.EMPTY;
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

    public void setRequiredAspects(AspectList aspects) {
        this.requiredAspects = aspects;
    }

    public void setWandStack(ItemStack wandStack) {
        this.wandStack = wandStack;
    }

    @Override
    public boolean mayPickup(Player player) {
        // AE2 原版：结果槽不可 vanilla 点按取走，所有取走走 doClick（含 vis 检查）
        return false;
    }

    @Override
    public void doClick(InventoryAction action, Player who) {
        // vis 检查：奥术配方需法杖有足够 vis，否则不合成
        if (requiredAspects != null && !requiredAspects.isEmpty()) {
            if (wandStack.isEmpty()) {
                return;
            }
            if (!(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) {
                return;
            }
            AspectList centivisCost = new AspectList();
            for (Aspect aspect : requiredAspects.aspects()) {
                centivisCost.add(aspect, requiredAspects.amount(aspect) * CENTIVIS);
            }
            if (!wand.consumeVisCost(wandStack, who, centivisCost, false, true)) {
                return;
            }
        }
        

        // 奥术配方（非 vanilla CRAFTING）不走 AE2 craftItem（findRecipe 只认 vanilla）
        // 走自定义合成：从合成格读配方 → 产物 + 消耗材料 + 从网络补货
        if (container != null && container.hasValidRecipe) {
            doArcaneCraft(action, who);
            return;
        }

        // vanilla 配方：严格 AE2（从网络提取材料填合成格）
        super.doClick(action, who);
        // 消耗 vis（实际扣除）
        if (requiredAspects != null && !requiredAspects.isEmpty() && !wandStack.isEmpty()) {
            if (wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand) {
                AspectList centivisCost = new AspectList();
                for (Aspect aspect : requiredAspects.aspects()) {
                    centivisCost.add(aspect, requiredAspects.amount(aspect) * CENTIVIS);
                }
                wand.consumeVisCost(wandStack, who, centivisCost, true, true);
            }
        }
    }

    /** 奥术配方合成：从合成格读 Thaumcraft 奥术配方 → 产物 + 消耗材料 + 从网络补货。 */
    private void doArcaneCraft(InventoryAction action, Player who) {
        if (this.getItem().isEmpty() || isRemote()) {
            return;
        }
        var grid = craftingGrid;
        var level = who.level();
        if (level.isClientSide()) return;

        // 用 Thaumcraft 奥术配方匹配（取配方对象以支持 retainIngredient 保留原料）
        var craftContainer = grid.toContainer();
        thaumcraft.api.crafting.IArcaneRecipe arcaneRecipe = thaumcraft.common.lib.crafting.ThaumcraftCraftingManager
                .findMatchingArcaneRecipeEntry(craftContainer, who);
        if (arcaneRecipe == null) {
            return;
        }
        ItemStack arcaneResult = arcaneRecipe.getCraftingResult(craftContainer);
        if (arcaneResult.isEmpty()) {
            return;
        }
        // 保留原料（retainIngredient，如元始珍珠）：getRemainingItems 返回非空栈的槽位不消耗。
        // 对应 TC 奥术工作台行为——珍珠作为"催化"留在合成格，仅消耗构材与虚空金属锭。
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> remainders =
                arcaneRecipe.getRemainingItems(craftContainer);
        

        // shift=整组到背包、普通=1 个到手
        int maxTimesToCraft;
        InternalInventory target;
        if (action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL) {
            target = new appeng.util.inv.PlayerInternalInventory(who.getInventory());
            maxTimesToCraft = (int) Math.floor((double) arcaneResult.getMaxStackSize() / (double) arcaneResult.getCount());
            if (action == InventoryAction.CRAFT_ALL) {
                maxTimesToCraft *= net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;
            }
        } else {
            target = new appeng.util.inv.CarriedItemInventory(getMenu());
            maxTimesToCraft = 1;
        }

        int crafted = 0;
        for (int x = 0; x < maxTimesToCraft; x++) {
            // 每轮 vis 检查 + 实际扣减（consume=true）：法杖 vis 不足则停
            if (requiredAspects != null && !requiredAspects.isEmpty()) {
                if (wandStack.isEmpty()) break;
                if (!(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) break;
                AspectList centivisCost = new AspectList();
                for (Aspect aspect : requiredAspects.aspects()) {
                    centivisCost.add(aspect, requiredAspects.amount(aspect) * CENTIVIS);
                }
                boolean ok = wand.consumeVisCost(wandStack, who, centivisCost, true, true);
                if (!ok) {
                    break;
                }
            }
            // 目标满或配方失效则停
            if (!target.simulateAdd(arcaneResult).isEmpty()) {
                break;
            }
            // 合成前检查奥术配方仍匹配
            ItemStack currentResult = thaumcraft.common.lib.crafting.ThaumcraftCraftingManager
                    .findMatchingArcaneRecipe(grid.toContainer(), who);
            if (currentResult.isEmpty()) {
                break;
            }

            // 记录消耗前合成格（首次记录，后续每轮消耗后补货）
            var beforeItems = new java.util.HashMap<Integer, ItemStack>();
            for (int i = 0; i < grid.size(); i++) {
                var s = grid.getStackInSlot(i);
                if (!s.isEmpty()) beforeItems.put(i, s.copy());
            }
            // 消耗合成格材料（每格 1 个）；保留原料槽（remainders 非空，如元始珍珠）不消耗
            for (int i = 0; i < grid.size(); i++) {
                if (!grid.getStackInSlot(i).isEmpty() && remainders.get(i).isEmpty()) {
                    grid.extractItem(i, 1, false);
                }
            }
            // 从网络补货（对齐 AE2 终端）
            if (storage != null && energySrc != null && mySrc != null) {
                for (var entry : beforeItems.entrySet()) {
                    int slot = entry.getKey();
                    var beforeItem = entry.getValue();
                    var currentItem = grid.getStackInSlot(slot);
                    if (currentItem.getCount() < beforeItem.getCount()) {
                        long needed = beforeItem.getCount() - currentItem.getCount();
                        var key = appeng.api.stacks.AEItemKey.of(beforeItem);
                        if (key != null) {
                            long extracted = appeng.api.storage.StorageHelper.poweredExtraction(
                                    energySrc, storage, key, needed, mySrc);
                            if (extracted > 0) {
                                grid.setItemDirect(slot, beforeItem.copyWithCount(
                                        (int) (currentItem.getCount() + extracted)));
                            }
                        }
                    }
                }
            }

            // 产物入目标
            var extra = target.addItems(arcaneResult.copy());
            if (!extra.isEmpty()) {
                appeng.util.Platform.spawnDrops(who.level(), who.blockPosition(), java.util.List.of(extra));
                break;
            }
            crafted++;
        }
        
        // 触发容器刷新（重算结果槽）
        container.slotsChanged(grid.toContainer());
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        if (container != null) {
            container.setCraftingResultBeingTaken(true);
        }
        try {
            // 记录消耗前各合成格物品，取走后从 ME 存储自动补回（AE2 终端行为）
            java.util.Map<Integer, ItemStack> beforeItems = new java.util.HashMap<>();
            var grid = craftingGrid;
            for (int i = 0; i < grid.size(); i++) {
                var s = grid.getStackInSlot(i);
                if (!s.isEmpty()) beforeItems.put(i, s.copy());
            }

            super.onTake(player, stack);

            if (requiredAspects != null && !requiredAspects.isEmpty() && !wandStack.isEmpty()) {
                if (wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand) {
                    AspectList centivisCost = new AspectList();
                    for (Aspect aspect : requiredAspects.aspects()) {
                        centivisCost.add(aspect, requiredAspects.amount(aspect) * CENTIVIS);
                    }
                    wand.consumeVisCost(wandStack, player, centivisCost, true, true);
                }
            }

            // 自动补货：合成格材料被消耗后从 ME 存储补回
            if (!beforeItems.isEmpty()) {
                if (storage != null && energySrc != null && mySrc != null) {
                    for (var entry : beforeItems.entrySet()) {
                        int slot = entry.getKey();
                        var beforeItem = entry.getValue();
                        var currentItem = grid.getStackInSlot(slot);
                        if (currentItem.getCount() < beforeItem.getCount()) {
                            long needed = beforeItem.getCount() - currentItem.getCount();
                            var key = appeng.api.stacks.AEItemKey.of(beforeItem);
                            if (key != null) {
                                long extracted = appeng.api.storage.StorageHelper.poweredExtraction(
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
