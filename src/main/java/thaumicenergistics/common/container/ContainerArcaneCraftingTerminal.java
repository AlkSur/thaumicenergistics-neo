package thaumicenergistics.common.container;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.CraftingMatrixSlot;
import appeng.me.storage.LinkStatusRespectingInventory;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import net.minecraft.world.item.crafting.Ingredient;
import thaumcraft.common.items.TCFunctionalItems;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.slot.ArcaneCraftingResultSlot;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.init.ModMenuTypes;

public class ContainerArcaneCraftingTerminal extends MEStorageMenu implements ICraftingGridMenu {

    @GuiSync(20)
    public String requiredAspectsData = "";

    @GuiSync(21)
    public boolean hasValidRecipe = false;

    /**
     * 防止 onTake 期间 slotsChanged 的中间态干扰配方检测。
     * 在 ArcaneCraftingResultSlot.onTake 开始前设为 true，完成后设为 false。
     */
    private boolean isCraftingResultBeingTaken = false;

    private final ISegmentedInventory craftingInventoryHost;
    private final CraftingMatrixSlot[] craftingSlots = new CraftingMatrixSlot[9];
    private final ArcaneCraftingResultSlot resultSlot;

    public ContainerArcaneCraftingTerminal(int id, Inventory ip, ITerminalHost host) {
        this(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(), id, ip, host, true);
    }

    public ContainerArcaneCraftingTerminal(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host,
                                           boolean bindInventory) {
        super(menuType, id, ip, host, bindInventory);
        this.craftingInventoryHost = (ISegmentedInventory) host;

        var craftingGridInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);

        for (int i = 0; i < 9; i++) {
            this.craftingSlots[i] = new CraftingMatrixSlot(this, craftingGridInv, i);
            this.addSlot(this.craftingSlots[i], SlotSemantics.CRAFTING_GRID);
        }

        var linkStatusInventory = new LinkStatusRespectingInventory(host.getInventory(), this::getLinkStatus);
        this.resultSlot = new ArcaneCraftingResultSlot(getPlayerInventory().player, craftingGridInv);
        this.resultSlot.setRefreshCallback(() -> updateCurrentRecipeAndOutput(false));
        this.resultSlot.setContainer(this);
        this.addSlot(this.resultSlot, SlotSemantics.CRAFTING_RESULT);

        var wandInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
        this.addSlot(new AppEngSlot(wandInv, ArcaneCraftingTerminalPart.WAND_SLOT_INDEX) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                // 法杖槽变化时立即重检配方，更新 resultSlot 的 wandStack
                updateCurrentRecipeAndOutput(false);
            }
        }, SlotSemantics.STORAGE);

        updateCurrentRecipeAndOutput(true);
    }

    // ========== ICraftingGridMenu 实现 ==========

    @Override
    public InternalInventory getCraftingMatrix() {
        var inv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
        ThaumicEnergistics.LOG.debug("[ICraftingGrid] getCraftingMatrix called, size={}", inv.size());
        return inv;
    }

    @Override
    public void startAutoCrafting(List<AutoCraftEntry> toCraft) {
        CraftConfirmMenu.openWithCraftingList(getActionHost(), (ServerPlayer) getPlayer(), getLocator(), toCraft);
    }

    @Override
    public IEnergySource getEnergySource() {
        return this.energySource;
    }

    // ========== 配方检测 ==========

    @Override
    public void slotsChanged(Container inventory) {
        ThaumicEnergistics.LOG.debug("[A] slotsChanged fired, beingTaken={}, inventoryClass={}",
                isCraftingResultBeingTaken, inventory.getClass().getSimpleName());
        if (!isCraftingResultBeingTaken) {
            updateCurrentRecipeAndOutput(false);
        } else {
            ThaumicEnergistics.LOG.debug("[A] slotsChanged SKIPPED (beingTaken=true)");
        }
    }

    public void setCraftingResultBeingTaken(boolean beingTaken) {
        ThaumicEnergistics.LOG.debug("[A] setCraftingResultBeingTaken={}", beingTaken);
        this.isCraftingResultBeingTaken = beingTaken;
    }

    private static final int CENTIVIS = 100;

    private boolean hasEnoughVis(AspectList cost) {
        ThaumicEnergistics.LOG.debug("[V] hasEnoughVis: cost={}", cost);
        if (cost == null || cost.isEmpty()) {
            ThaumicEnergistics.LOG.debug("[V] no cost, no wand needed");
            return true;
        }
        var wandInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
        var wandStack = wandInv.getStackInSlot(0);
        ThaumicEnergistics.LOG.debug("[V] wandStack={}", wandStack);
        if (wandStack.isEmpty()) {
            ThaumicEnergistics.LOG.debug("[V] wandStack EMPTY");
            return false;
        }
        if (!(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) {
            ThaumicEnergistics.LOG.debug("[V] not a WandCastingItem");
            return false;
        }
        var centivisCost = new AspectList();
        for (var aspect : cost.aspects()) {
            centivisCost.add(aspect, cost.amount(aspect) * CENTIVIS);
        }
        boolean result = wand.consumeVisCost(wandStack, getPlayerInventory().player, centivisCost, false, true);
        ThaumicEnergistics.LOG.debug("[V] consumeVisCost result={}", result);
        return result;
    }

    private void updateCurrentRecipeAndOutput(boolean forceUpdate) {
        ThaumicEnergistics.LOG.debug("[R] === updateCurrentRecipeAndOutput forceUpdate={} ===", forceUpdate);
        var player = getPlayerInventory().player;
        var level = player.level();

        boolean foundArcane = false;
        AspectList requiredAspects = null;

        if (level.getServer() != null) {
            var craftContainer = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING).toContainer();

            ItemStack arcaneResult = ThaumcraftCraftingManager.findMatchingArcaneRecipe(craftContainer, player);
            ThaumicEnergistics.LOG.debug("[R] findMatchingArcaneRecipe result={}", arcaneResult);

            if (!arcaneResult.isEmpty()) {
                requiredAspects = ThaumcraftCraftingManager.findMatchingArcaneRecipeAspects(craftContainer, player);
                ThaumicEnergistics.LOG.debug("[R] ARCANE MATCH! aspects={}, result={}", requiredAspects, arcaneResult);
                foundArcane = true;

                if (hasEnoughVis(requiredAspects)) {
                    ThaumicEnergistics.LOG.debug("[R] Vis OK → showing result");
                    resultSlot.setDisplayedCraftingOutput(arcaneResult);
                } else {
                    ThaumicEnergistics.LOG.debug("[R] Vis INSUFFICIENT → result empty, but recipe recognized");
                    resultSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
                }
            } else {
                ThaumicEnergistics.LOG.debug("[R] No arcane recipe match");
            }
        }

        if (!foundArcane) {
            ThaumicEnergistics.LOG.debug("[R] No arcane match, trying vanilla...");
            var testItems = new ArrayList<ItemStack>(this.craftingSlots.length);
            for (var craftingSlot : this.craftingSlots) {
                testItems.add(craftingSlot.getItem().copy());
            }
            var testInput = CraftingInput.of(3, 3, testItems);
            var recipeHolder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, testInput, level);
            if (recipeHolder.isPresent()) {
                ItemStack result = recipeHolder.get().value().assemble(testInput, level.registryAccess());
                ThaumicEnergistics.LOG.debug("[R] Vanilla match: {}, setting result slot", result);
                resultSlot.setDisplayedCraftingOutput(result);
            } else {
                ThaumicEnergistics.LOG.debug("[R] No vanilla match either, clearing result slot");
                // 客户端不清空产物槽，@GuiSync 会同步服务端的正确状态
                if (!level.isClientSide()) {
                    resultSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
                }
            }
        }

        if (foundArcane) {
            // 服务端设置 @GuiSync 字段；客户端不应覆盖来自服务端的同步值
            ThaumicEnergistics.LOG.debug("[R] foundArcane branch: isClientSide={}", level.isClientSide());
            if (level.isClientSide()) {
                ThaumicEnergistics.LOG.debug("[R] Client: keeping server-synced values, not overriding");
                return;
            }
            this.hasValidRecipe = true;
            resultSlot.setRequiredAspects(requiredAspects);
            var wandInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
            var ws = wandInv.getStackInSlot(0);
            resultSlot.setWandStack(ws);
            ThaumicEnergistics.LOG.debug("[R] SET state: hasValidRecipe=true, wandStack={}", ws);

            if (requiredAspects != null && requiredAspects.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Aspect, Integer> entry : requiredAspects.aspects.entrySet()) {
                    if (!sb.isEmpty()) sb.append(",");
                    sb.append(entry.getKey().getTag()).append("=").append(entry.getValue());
                }
                this.requiredAspectsData = sb.toString();
                ThaumicEnergistics.LOG.debug("[R] aspectsData={}", this.requiredAspectsData);
            } else {
                this.requiredAspectsData = "";
                ThaumicEnergistics.LOG.debug("[R] aspectsData=empty (no aspects)");
            }
        } else {
            ThaumicEnergistics.LOG.debug("[R] else branch: isClientSide={}, levelClass={}",
                    level.isClientSide(), level.getClass().getSimpleName());
            if (level.isClientSide()) {
                ThaumicEnergistics.LOG.debug("[R] Client: not overriding synced values (no arcane recipe on client)");
                return;
            }
            this.requiredAspectsData = "";
            this.hasValidRecipe = false;
            resultSlot.setRequiredAspects(null);
            resultSlot.setWandStack(ItemStack.EMPTY);
            ThaumicEnergistics.LOG.debug("[R] SET state: hasValidRecipe=false (no arcane recipe)");
        }
    }

    public Map<String, Integer> getParsedAspects() {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (requiredAspectsData.isEmpty()) return map;
        for (String part : requiredAspectsData.split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                try {
                    map.put(kv[0], Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }

    // ========== JEI 配方转移支持 ==========

    /**
     * 确定给定 slot→ingredient 映射中哪些槽位无法从 ME 网络或玩家背包填充。
     * 移植自 CraftingTermMenu.findMissingIngredients。
     */
    public MissingIngredientSlots findMissingIngredients(Map<Integer, Ingredient> ingredients) {
        Set<Integer> missingSlots = new HashSet<>();
        Set<Integer> craftableSlots = new HashSet<>();

        var reservedGridAmounts = new Object2IntOpenHashMap<Object>();
        var playerItems = getPlayerInventory().items;
        var reservedPlayerItems = new int[playerItems.size()];

        for (var entry : ingredients.entrySet()) {
            var ingredient = entry.getValue();
            boolean found = false;

            for (int i = 0; i < playerItems.size(); i++) {
                if (isPlayerInventorySlotLocked(i)) continue;
                var stack = playerItems.get(i);
                if (stack.getCount() - reservedPlayerItems[i] > 0 && ingredient.test(stack)) {
                    reservedPlayerItems[i]++;
                    found = true;
                    break;
                }
            }

            if (!found) {
                if (hasIngredient(ingredient, reservedGridAmounts)) {
                    reservedGridAmounts.merge(ingredient, 1, Integer::sum);
                    found = true;
                }
            }

            if (!found) {
                for (var stack : ingredient.getItems()) {
                    if (isCraftable(stack)) {
                        craftableSlots.add(entry.getKey());
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                missingSlots.add(entry.getKey());
            }
        }

        return new MissingIngredientSlots(missingSlots, craftableSlots);
    }

    /**
     * 检查 ME 网络中是否有指定材料的库存（含已预留量）。
     * 直接遍历 clientRepo 条目匹配 ingredient，确保不受 linkStatus 时序或 getByIngredient 实现细节影响。
     */
    public boolean hasIngredient(Ingredient ingredient, Object2IntOpenHashMap<Object> reservedAmounts) {
        for (var slot : getSlots(SlotSemantics.CRAFTING_GRID)) {
            var stackInSlot = slot.getItem();
            if (!stackInSlot.isEmpty() && ingredient.test(stackInSlot)) {
                var reservedAmount = reservedAmounts.getOrDefault(slot, 0);
                if (stackInSlot.getCount() > reservedAmount) {
                    reservedAmounts.merge(slot, 1, Integer::sum);
                    return true;
                }
            }
        }
        // 直接遍历 clientRepo 检查 ME 网络仓库
        var clientRepo = getClientRepo();
        if (clientRepo != null) {
            for (var entry : clientRepo.getAllEntries()) {
                if (!(entry.getWhat() instanceof AEItemKey itemKey)) continue;
                if (entry.getStoredAmount() <= 0) continue;
                if (!ingredient.test(itemKey.toStack())) continue;
                var reservedAmount = reservedAmounts.getOrDefault(entry, 0);
                if (entry.getStoredAmount() - reservedAmount >= 1) {
                    reservedAmounts.merge(entry, 1, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查物品是否可在 ME 网络中自动合成。
     * 移植自 CraftingTermMenu.isCraftable。
     */
    private boolean isCraftable(ItemStack itemStack) {
        var clientRepo = getClientRepo();
        if (clientRepo != null) {
            for (var stack : clientRepo.getAllEntries()) {
                if (AEItemKey.matches(stack.getWhat(), itemStack) && stack.isCraftable()) {
                    return true;
                }
            }
        }
        return false;
    }

    public record MissingIngredientSlots(Set<Integer> missingSlots, Set<Integer> craftableSlots) {
        public int totalSize() {
            return missingSlots.size() + craftableSlots.size();
        }

        public boolean anyMissingOrCraftable() {
            return anyMissing() || anyCraftable();
        }

        public boolean anyMissing() {
            return !missingSlots.isEmpty();
        }

        public boolean anyCraftable() {
            return !craftableSlots.isEmpty();
        }
    }
}
