package tc_energy.common.container.slot;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingApi;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingRecipe;
import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import tc_energy.common.container.ContainerArcaneCraftingTerminal;

/**
 * 奥术合成结果槽：继承 AE2 CraftingTermSlot 使客户端识别（shift/右键/space 合成自动生效），
 * 并保留 vis 消耗检查（mayPickup）与取走时 vis 扣除 + 自动补货（onTake）。
 * Ren 版：vis 支付走 WandApi.craftingCost + ThaumcraftApiHelper.consumeExactVisFromWand；
 * 配方匹配走 ArcaneCraftingApi.recipes(level)（Ren 版 Recipe.matches）。
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    /** 配方 whole-vis → centivis 换算（存储/判定均以 centivis 计）。 */
    private static final int CENTIVIS = 100;

    /** 当前配方的 whole-vis 成本（Ren 版配方 vis()），null = 无 vis 要求。 */
    private Map<VisChannel, Integer> requiredVis = null;
    /** 当前配方的研究要求（whole-vis 折扣在 WandApi.craftingCost 内处理）。 */
    private java.util.List<ResearchKey> requiredResearch = java.util.List.of();
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

    public void setRequiredVis(Map<VisChannel, Integer> vis) {
        this.requiredVis = vis;
    }

    public void setRequiredResearch(java.util.List<ResearchKey> research) {
        this.requiredResearch = research == null ? java.util.List.of() : research;
    }

    public void setWandStack(ItemStack wandStack) {
        this.wandStack = wandStack;
    }

    // ===== Ren 版 vis 支付 =====

    /** 计算折后 centivis 成本（含法杖 cap/装备折扣）。无法杖返回 null。 */
    private Map<VisChannel, Integer> discountedCentivisCost(Player player) {
        if (requiredVis == null || requiredVis.isEmpty()) {
            return Map.of();
        }
        if (wandStack.isEmpty() || !dev.tc4port.thaumcraft.api.wand.WandApi.supportsArcaneCrafting(wandStack)) {
            return null;
        }
        return dev.tc4port.thaumcraft.api.wand.WandApi.craftingCost(wandStack, player, requiredVis);
    }

    /** SIMULATE：法杖是否支付得起当前配方 vis。 */
    private boolean hasEnoughVis(Player player) {
        Map<VisChannel, Integer> centivis = discountedCentivisCost(player);
        if (centivis == null) {
            return requiredVis == null || requiredVis.isEmpty(); // 无 vis 要求=满足；有要求无法杖=不满足
        }
        if (centivis.isEmpty()) {
            return true;
        }
        if (player.level().isClientSide()) {
            // 客户端预测：WandPayments 客户端可用（SIMULATE 不改状态）
            return ThaumcraftApiHelper.consumeExactVisFromWand(wandStack, player, centivis, VisAction.SIMULATE).affordable();
        }
        return ThaumcraftApiHelper.consumeExactVisFromWand(wandStack, player, centivis, VisAction.SIMULATE).affordable();
    }

    /** EXECUTE：实际扣 vis。EXECUTE 成功返回 CONSUMED（Ren 版语义）。 */
    private boolean consumeVis(Player player) {
        Map<VisChannel, Integer> centivis = discountedCentivisCost(player);
        if (centivis == null) {
            return false;
        }
        if (centivis.isEmpty()) {
            return true;
        }
        return ThaumcraftApiHelper.consumeExactVisFromWand(wandStack, player, centivis, VisAction.EXECUTE).consumed();
    }

    @Override
    public boolean mayPickup(Player player) {
        // AE2 原版：结果槽不可 vanilla 点击取走，所有取走走 doClick（含 vis 检查）
        return false;
    }

    @Override
    public void doClick(InventoryAction action, Player who) {
        // vis 检查：奥术配方需法杖有足够 vis，否则不合成的
        if (requiredVis != null && !requiredVis.isEmpty()) {
            if (!hasEnoughVis(who)) {
                return;
            }
        }
        // 研究检查：奥术配方要求的研究未解锁 → 拒绝（设备端检查，替代旧 MixinCraftingService）
        if (!requiredResearch.isEmpty() && !hasRequiredResearch(who)) {
            return;
        }

        // 奥术配方（非 vanilla CRAFTING）不走 AE2 craftItem（findRecipe 只认 vanilla），
        // 走自定义合成：从合成格读配方 → 产物 + 消耗材料 + 从网络补货
        if (container != null && container.hasValidRecipe) {
            doArcaneCraft(action, who);
            return;
        }

        // vanilla 配方：严格 AE2（从网络提取材料填合成格）
        super.doClick(action, who);
        // 消耗 vis（实际扣除）
        if (requiredVis != null && !requiredVis.isEmpty()) {
            consumeVis(who);
        }
    }

    private boolean hasRequiredResearch(Player player) {
        for (ResearchKey key : requiredResearch) {
            if (!ResearchApi.isComplete(player, key)) {
                return false;
            }
        }
        return true;
    }

    /** 在合成格中匹配 Ren 版奥术配方（第一个 matches 的）。 */
    private Optional<RecipeHolder<ArcaneCraftingRecipe>> findArcaneRecipe(net.minecraft.world.item.crafting.CraftingInput input, net.minecraft.world.level.Level level) {
        for (RecipeHolder<ArcaneCraftingRecipe> holder : ArcaneCraftingApi.recipes(level)) {
            if (holder.value().matches(input, level)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    /** 奥术配方合成：从合成格读 Thaumcraft 奥术配方 → 产物 + 消耗材料 + 从网络补货。 */
    private void doArcaneCraft(InventoryAction action, Player who) {
        if (this.getItem().isEmpty() || isRemote()) {
            return;
        }
        var grid = craftingGrid;
        var level = who.level();
        if (level.isClientSide()) return;

        var craftContainer = grid.toContainer();
        net.minecraft.world.item.crafting.CraftingInput input = toCraftingInput(grid);
        var recipeOpt = findArcaneRecipe(input, level);
        if (recipeOpt.isEmpty()) {
            return;
        }
        ArcaneCraftingRecipe arcaneRecipe = recipeOpt.get().value();
        RegistryAccess registryAccess = level.registryAccess();
        // 动态配方（法杖组装）按实际合成格计算产物；getResultItem 只会给默认的展示物
        ItemStack arcaneResult = arcaneRecipe.assemble(input, registryAccess);
        if (arcaneResult.isEmpty()) {
            return;
        }
        // 保留原料（remainders 非空，如元始珍珠）：这些槽位不消耗
        var remainders = arcaneRecipe.getRemainingItems(input);

        // shift=整组到背包、普通 1 个到手
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
            // 每轮 vis 检查 + 实际扣量（EXECUTE）；法杖 vis 不足则停
            if (requiredVis != null && !requiredVis.isEmpty()) {
                if (!consumeVis(who)) break;
            }
            // 目标满或配方失效则停
            if (!target.simulateAdd(arcaneResult).isEmpty()) {
                break;
            }
            // 合成前检查奥术配方仍匹配
            var currentInput = toCraftingInput(grid);
            if (findArcaneRecipe(currentInput, level).isEmpty()) {
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
                boolean retained = i < remainders.size() && !remainders.get(i).isEmpty();
                if (!grid.getStackInSlot(i).isEmpty() && !retained) {
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

    /** InternalInventory → CraftingInput（Ren 版 Recipe.matches 需要）。 */
    private static net.minecraft.world.item.crafting.CraftingInput toCraftingInput(InternalInventory grid) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(grid.size());
        for (int i = 0; i < grid.size(); i++) {
            items.add(grid.getStackInSlot(i));
        }
        return net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
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

            if (requiredVis != null && !requiredVis.isEmpty() && !wandStack.isEmpty()) {
                consumeVis(player);
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
