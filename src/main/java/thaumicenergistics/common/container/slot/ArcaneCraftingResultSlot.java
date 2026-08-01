package thaumicenergistics.common.container.slot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngCraftingSlot;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.TCFunctionalItems;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;

/**
 * 奥术合成结果槽：在取走合成结果时消耗法杖要素。
 * 参考 TC4 1.21.1 ArcaneWorkbenchMenu.onResultTaken + consumeVis。
 */
public class ArcaneCraftingResultSlot extends AppEngCraftingSlot {

    /** TC4 内部使用 centivis，1 vis = 100 centivis */
    private static final int CENTIVIS = 100;

    private AspectList requiredAspects = null;
    private ItemStack wandStack = ItemStack.EMPTY;
    private Runnable refreshCallback = null;
    private ContainerArcaneCraftingTerminal container = null;

    public ArcaneCraftingResultSlot(Player player, InternalInventory craftingGridInv) {
        super(player, craftingGridInv);
    }

    public void setContainer(ContainerArcaneCraftingTerminal container) {
        this.container = container;
    }

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    public void setRequiredAspects(AspectList aspects) {
        this.requiredAspects = aspects;
    }

    public void setWandStack(ItemStack wandStack) {
        this.wandStack = wandStack;
    }

    @Override
    public boolean mayPickup(Player player) {
        if (requiredAspects == null || requiredAspects.isEmpty()) {
            return super.mayPickup(player);
        }

        if (wandStack.isEmpty()) return false;
        if (!(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) {
            return false;
        }

        AspectList centivisCost = new AspectList();
        for (Aspect aspect : requiredAspects.aspects()) {
            centivisCost.add(aspect, requiredAspects.amount(aspect) * CENTIVIS);
        }
        return wand.consumeVisCost(wandStack, player, centivisCost, false, true);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        // 记录消耗前各合成格中的物品类型，用于消耗后从 ME 网络补充
        java.util.Map<Integer, ItemStack> beforeItems = new java.util.HashMap<>();
        if (container != null) {
            var grid = container.getCraftingMatrix();
            for (int i = 0; i < grid.size(); i++) {
                var s = grid.getStackInSlot(i);
                if (!s.isEmpty()) {
                    beforeItems.put(i, s.copy());
                }
            }
        }

        if (container != null) {
            container.setCraftingResultBeingTaken(true);
        }
        try {
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

            // 从 ME 网络自动补充消耗掉的合成材料
            if (container != null && !beforeItems.isEmpty()) {
                var grid = container.getCraftingMatrix();
                var node = container.getGridNode();
                if (node != null && node.getGrid() != null) {
                    var storage = node.getGrid().getStorageService().getInventory();
                    var energy = container.getEnergySource();
                    var actionSource = container.getActionSource();
                    for (var entry : beforeItems.entrySet()) {
                        int slot = entry.getKey();
                        var beforeItem = entry.getValue();
                        var currentItem = grid.getStackInSlot(slot);
                        // 如果该格物品变少了（被消耗了），从网络提取补充
                        if (currentItem.getCount() < beforeItem.getCount()) {
                            long needed = beforeItem.getCount() - currentItem.getCount();
                            var key = appeng.api.stacks.AEItemKey.of(beforeItem);
                            if (key != null) {
                                long extracted = appeng.api.storage.StorageHelper.poweredExtraction(
                                        energy, storage, key, needed, actionSource);
                                if (extracted > 0) {
                                    var toPlace = beforeItem.copyWithCount((int) (currentItem.getCount() + extracted));
                                    grid.setItemDirect(slot, toPlace);
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
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }
}
