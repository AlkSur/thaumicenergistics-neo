package tc_energy.common.container;

import java.util.ArrayList;
import java.util.HashMap;
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
import appeng.api.storage.StorageHelper;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.CraftingMatrixSlot;
import appeng.me.storage.LinkStatusRespectingInventory;
import appeng.util.inv.PlayerInternalInventory;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.neoforged.neoforge.network.PacketDistributor;
import tc_energy.common.network.ArcaneTerminalClearC2SPacket;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import net.minecraft.world.item.crafting.Ingredient;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingApi;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingRecipe;
import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import tc_energy.common.container.slot.ArcaneCraftingResultSlot;
import tc_energy.common.parts.ArcaneCraftingTerminalPart;
import tc_energy.init.ModMenuTypes;

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

    /** vis 恢复检测节流：每 10 tick（0.5 秒）检查一次，避免持续遍历配方。 */
    private static final int VIS_RECOVERY_CHECK_INTERVAL = 10;
    private int visRecoveryTickCounter = 0;

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // vis 充能恢复后自动重算结果槽：结果槽空 + 奥术配方有效 + vis 已足够 → 重新显示产物。
        // 节流：仅"待恢复"状态每 10 tick 检查一次（findMatchingArcaneRecipe 只在满足时执行）
        var player = getPlayerInventory().player;
        if (player.level().isClientSide() || !hasValidRecipe || !resultSlot.getItem().isEmpty()) {
            return;
        }
        if (++visRecoveryTickCounter < VIS_RECOVERY_CHECK_INTERVAL) {
            return;
        }
        visRecoveryTickCounter = 0;
        var craftingInput = toCraftingInput();
        var matched = findArcaneRecipe(craftingInput);
        if (matched.isPresent()) {
            ArcaneCraftingRecipe recipe = matched.get().value();
            // 必须用带合成格的 vis(input)：法杖组装是动态配方，无参 vis() 返回空 → 不扣 vis
            Map<VisChannel, Integer> vis = recipe.vis(craftingInput);
            if (hasEnoughVis(vis)) {
                resultSlot.setRequiredVis(vis);
                resultSlot.setRequiredResearch(recipe.requiredResearch(craftingInput));
                // 动态配方（法杖组装 WandAssemblyRecipe）必须用 assemble(input)：getResultItem 只返回默认展示物（铁法杖）
                resultSlot.setDisplayedCraftingOutput(recipe.assemble(craftingInput, player.level().registryAccess()));
            }
        }
    }

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
        this.resultSlot = new ArcaneCraftingResultSlot(
                getPlayerInventory().player, this, craftingGridInv, craftingGridInv,
                linkStatusInventory, this.getEnergySource(), this.getActionSource());
        this.addSlot(this.resultSlot, SlotSemantics.CRAFTING_RESULT);

        var wandInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
        this.addSlot(new AppEngSlot(wandInv, ArcaneCraftingTerminalPart.WAND_SLOT_INDEX) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                // 只允许 Ren 版法杖（WandItem / WandCarrierItem）放入
                return dev.tc4port.thaumcraft.api.wand.WandApi.supportsArcaneCrafting(stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                // 法杖槽变化时立即重检配方，更新 resultSlot 的 wandStack
                updateCurrentRecipeAndOutput(false);
            }
        }, SlotSemantics.STORAGE);

        updateCurrentRecipeAndOutput(true);

        registerClientAction(ACTION_CLEAR_TO_PLAYER, this::clearToPlayerInventory);
    }

    // ========== ICraftingGridMenu 实现 ==========

    @Override
    public InternalInventory getCraftingMatrix() {
        return this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
    }

    @Override
    public void startAutoCrafting(List<AutoCraftEntry> toCraft) {
        CraftConfirmMenu.openWithCraftingList(getActionHost(), (ServerPlayer) getPlayer(), getLocator(), toCraft);
    }

    @Override
    public IEnergySource getEnergySource() {
        return this.energySource;
    }

    /**
     * 清空合成网格，材料一键存入 ME 网络（对应终端 S_STASH 按钮与"关闭时自动清理合成槽"设置）。
     * 仅客户端调用：发送专用 C2S 包，服务端直接执行 {@link #clearGridToNetworkOnServer()}
     * （不走 AE2 quickMove/MOVE_REGION 语义链，任何环境下都可靠）。
     */
    public void clearCraftingGrid() {
        if (!isClientSide()) {
            return;
        }
        PacketDistributor.sendToServer(
                new ArcaneTerminalClearC2SPacket(this.containerId, ArcaneTerminalClearC2SPacket.ACTION_STASH, false));
    }

    /** 服务端：把合成网格所有物品存入 ME 网络，放不下的回玩家背包。结果槽由 slotsChanged 联动刷新。 */
    public void clearGridToNetworkOnServer() {
        var grid = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
        var playerInv = new PlayerInternalInventory(getPlayerInventory());
        boolean changed = false;
        for (int i = 0; i < grid.size(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            changed = true;

            long inserted = 0;
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && canInteractWithGrid()) {
                inserted = StorageHelper.poweredInsert(energySource, storage, key, stack.getCount(),
                        this.getActionSource());
            }

            if (inserted >= stack.getCount()) {
                grid.setItemDirect(i, ItemStack.EMPTY);
            } else {
                ItemStack remaining = stack.copy();
                remaining.shrink((int) inserted);
                ItemStack left = playerInv.addItems(remaining);
                grid.setItemDirect(i, left.isEmpty() ? ItemStack.EMPTY : left);
            }
        }
        if (changed) {
            slotsChanged(grid.toContainer());
        }
    }

    /** 服务端：客户端上报的"关闭时自动清理合成槽"开关值（在菜单构造后由 C2S 包设置）。 */
    private boolean clearOnClose = false;

    public void setClearOnClose(boolean clearOnClose) {
        this.clearOnClose = clearOnClose;
    }

    /**
     * 菜单关闭时（无论 ESC、走开、方块被拆、服务端强制关闭），按客户端上报的开关清理网格。
     * 不依赖 GUI onClose 是否触发——这是最可靠的兜底。
     */
    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        if (!isClientSide() && clearOnClose) {
            clearGridToNetworkOnServer();
        }
        super.removed(player);
    }

    private static final String ACTION_CLEAR_TO_PLAYER = "clearToPlayer";

    /**
     * 清空合成网格，材料一键存入玩家背包（对应终端 S_STASH_TO_PLAYER_INV 按钮）。
     * 复刻 {@code appeng.menu.me.items.CraftingTermMenu.clearToPlayerInventory}。
     */
    public void clearToPlayerInventory() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_TO_PLAYER);
            return;
        }

        var craftingGridInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
        var playerInv = new PlayerInternalInventory(getPlayerInventory());

        for (int i = 0; i < craftingGridInv.size(); ++i) {
            for (int emptyLoop = 0; emptyLoop < 2; ++emptyLoop) {
                boolean allowEmpty = emptyLoop == 1;

                // 快捷栏优先
                final int HOTBAR_SIZE = 9;
                for (int j = HOTBAR_SIZE; j-- > 0;) {
                    if (playerInv.getStackInSlot(j).isEmpty() == allowEmpty) {
                        craftingGridInv.setItemDirect(i,
                                playerInv.getSlotInv(j).addItems(craftingGridInv.getStackInSlot(i)));
                    }
                }
                // 其余背包
                for (int j = HOTBAR_SIZE; j < Inventory.INVENTORY_SIZE; ++j) {
                    if (playerInv.getStackInSlot(j).isEmpty() == allowEmpty) {
                        craftingGridInv.setItemDirect(i,
                                playerInv.getSlotInv(j).addItems(craftingGridInv.getStackInSlot(i)));
                    }
                }
            }
        }
    }

    // ========== 配方检测 ==========

    @Override
    public void slotsChanged(Container inventory) {
        if (!isCraftingResultBeingTaken) {
            updateCurrentRecipeAndOutput(false);
        }
    }

    public void setCraftingResultBeingTaken(boolean beingTaken) {
        this.isCraftingResultBeingTaken = beingTaken;
    }

    private static final int CENTIVIS = 100;

    /**
     * Ren 版：折扣（法杖 cap/装备）由 WandApi.craftingCost 统一处理；
     * 本方法只保留 hasEnoughVis 的 SIMULATE 语义。
     */
    private boolean hasEnoughVis(Map<VisChannel, Integer> wholeVis) {
        if (wholeVis == null || wholeVis.isEmpty()) {
            return true;
        }
        var wandInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
        var wandStack = wandInv.getStackInSlot(ArcaneCraftingTerminalPart.WAND_SLOT_INDEX);
        if (wandStack.isEmpty() || !WandApi.supportsArcaneCrafting(wandStack)) {
            return false;
        }
        var centivisCost = WandApi.craftingCost(wandStack, getPlayerInventory().player, wholeVis);
        if (centivisCost.isEmpty()) {
            return true;
        }
        VisPaymentResult result = ThaumcraftApiHelper.consumeExactVisFromWand(
                wandStack, getPlayerInventory().player, centivisCost, VisAction.SIMULATE);
        return result.affordable();
    }

    /** InternalInventory(3x3) → CraftingInput。 */
    public net.minecraft.world.item.crafting.CraftingInput toCraftingInput() {
        var grid = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
        java.util.List<ItemStack> items = new java.util.ArrayList<>(grid.size());
        for (int i = 0; i < grid.size(); i++) {
            items.add(grid.getStackInSlot(i));
        }
        return net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
    }

    /** 匹配 Ren 版奥术配方（第一个 matches 的）。 */
    public java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<ArcaneCraftingRecipe>> findArcaneRecipe(
            net.minecraft.world.item.crafting.CraftingInput input) {
        var level = getPlayerInventory().player.level();
        for (net.minecraft.world.item.crafting.RecipeHolder<ArcaneCraftingRecipe> holder : ArcaneCraftingApi.recipes(level)) {
            if (holder.value().matches(input, level)) {
                return java.util.Optional.of(holder);
            }
        }
        return java.util.Optional.empty();
    }

    private void updateCurrentRecipeAndOutput(boolean forceUpdate) {
        var player = getPlayerInventory().player;
        var level = player.level();

        boolean foundArcane = false;
        Map<VisChannel, Integer> requiredVis = null;
        java.util.List<dev.tc4port.thaumcraft.api.research.ResearchKey> requiredResearchKeys = java.util.List.of();
        ItemStack arcaneResult = ItemStack.EMPTY;

        if (level.getServer() != null) {
            var craftingInput = toCraftingInput();
            var matched = findArcaneRecipe(craftingInput);

            if (matched.isPresent()) {
                ArcaneCraftingRecipe recipe = matched.get().value();
                // 同上：动态配方按实际合成格计算 vis 花费（杖端/杖芯不同花费不同）
                requiredVis = recipe.vis(craftingInput);
                requiredResearchKeys = recipe.requiredResearch(craftingInput);
                // 同上：动态配方按实际合成格计算产物（杖端/杖芯 → 对应法杖）
                arcaneResult = recipe.assemble(craftingInput, level.registryAccess());
                foundArcane = true;

                if (hasEnoughVis(requiredVis)) {
                    resultSlot.setDisplayedCraftingOutput(arcaneResult);
                } else {
                    resultSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
                }
            }
        }

        if (!foundArcane) {
            var testItems = new ArrayList<ItemStack>(this.craftingSlots.length);
            for (var craftingSlot : this.craftingSlots) {
                testItems.add(craftingSlot.getItem().copy());
            }
            var testInput = CraftingInput.of(3, 3, testItems);
            var recipeHolder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, testInput, level);
            if (recipeHolder.isPresent()) {
                ItemStack result = recipeHolder.get().value().assemble(testInput, level.registryAccess());
                resultSlot.setDisplayedCraftingOutput(result);
            } else {
                // 客户端不清空产物槽，@GuiSync 会同步服务端的正确状态
                if (!level.isClientSide()) {
                    resultSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
                }
            }
        }

        if (foundArcane) {
            // 服务端设置 @GuiSync 字段；客户端不应覆盖来自服务端的同步值
            if (level.isClientSide()) {
                return;
            }
            this.hasValidRecipe = true;
            resultSlot.setRequiredVis(requiredVis);
            resultSlot.setRequiredResearch(requiredResearchKeys);
            var wandInv = this.craftingInventoryHost.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
            var ws = wandInv.getStackInSlot(ArcaneCraftingTerminalPart.WAND_SLOT_INDEX);
            resultSlot.setWandStack(ws);

            if (requiredVis != null && !requiredVis.isEmpty()) {
                // 与 TC 奥术工作台 ArcaneWorktableMenu#visibleCentivisCost 对齐：
                // 显示的是「折后 centivis」而不是配方原始 whole-vis，否则法杖杖端/装备的
                // 魔力减免（WandApi.craftingCost 内的 consumptionModifier）在 GUI 上完全看不出来。
                Map<VisChannel, Integer> displayedCost;
                if (!ws.isEmpty() && WandApi.supportsArcaneCrafting(ws)) {
                    displayedCost = WandApi.craftingCost(ws, player, requiredVis);
                } else {
                    // 无法杖：退回未打折的 whole-vis → centivis，保持与工作台一致的显示单位
                    displayedCost = new LinkedHashMap<>();
                    for (Map.Entry<VisChannel, Integer> e : requiredVis.entrySet()) {
                        displayedCost.put(e.getKey(), e.getValue() * CENTIVIS);
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<VisChannel, Integer> entry : displayedCost.entrySet()) {
                    if (!sb.isEmpty()) sb.append(",");
                    // 存折后 centivis（1 vis = 100 centivis）；GUI 再按 vis 格式化
                    sb.append(entry.getKey().aspectId().serialized()).append("=")
                            .append(entry.getValue());
                }
                this.requiredAspectsData = sb.toString();
            } else {
                this.requiredAspectsData = "";
            }
        } else {
            if (level.isClientSide()) {
                return;
            }
            this.requiredAspectsData = "";
            this.hasValidRecipe = false;
            resultSlot.setRequiredVis(null);
            resultSlot.setRequiredResearch(java.util.List.of());
            resultSlot.setWandStack(ItemStack.EMPTY);
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
     * 为 JEI "+" 填充挑选一个可用的材料模板：优先取 ME 网络中实际有货的变体，
     * 其次玩家背包有货的变体，再次可自动合成的变体，最后回退到第一个变体。
     * <p>
     * 背景：奥术配方 recipeId=null，服务端只能用客户端模板重建单物品 Ingredient，
     * 若客户端固定发第一个变体（如石头），网络里只有深板岩/圆石时槽位就拉不到东西。
     * 这里带预留计数（reservedAmounts），多个槽位需要同一材料时不会重复占用同一份库存。
     *
     * @param stored    网络库存快照（AEItemKey → 数量），由调用方一次构建，避免每槽全量扫描
     * @param craftable 网络中可自动合成的物品集合快照
     */
    public ItemStack pickAvailableTemplate(Ingredient ingredient, Map<AEItemKey, Long> stored,
                                           Set<AEItemKey> craftable, Object2IntOpenHashMap<AEItemKey> reservedAmounts) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }

        // 1) ME 网络中有库存的变体
        for (ItemStack variant : items) {
            AEItemKey key = AEItemKey.of(variant);
            if (key == null) continue;
            long avail = stored.getOrDefault(key, 0L) - reservedAmounts.getOrDefault(key, 0);
            if (avail >= 1) {
                reservedAmounts.merge(key, 1, Integer::sum);
                return variant.copyWithCount(1);
            }
        }

        // 2) 玩家背包中的变体
        var playerItems = getPlayerInventory().items;
        for (int i = 0; i < playerItems.size(); i++) {
            if (isPlayerInventorySlotLocked(i)) continue;
            var stack = playerItems.get(i);
            if (stack.isEmpty() || !ingredient.test(stack)) continue;
            AEItemKey key = AEItemKey.of(stack);
            int used = key != null ? reservedAmounts.getOrDefault(key, 0) : 0;
            if (stack.getCount() - used >= 1) {
                if (key != null) {
                    reservedAmounts.merge(key, 1, Integer::sum);
                }
                return stack.copyWithCount(1);
            }
        }

        // 3) 可自动合成的变体（仅当玩家按住 Ctrl 时服务端才会真正排产；不按则槽位保持空，与旧行为一致）
        for (ItemStack variant : items) {
            AEItemKey key = AEItemKey.of(variant);
            if (key != null && craftable.contains(key) && reservedAmounts.getOrDefault(key, 0) == 0) {
                reservedAmounts.merge(key, 1, Integer::sum);
                return variant.copyWithCount(1);
            }
        }

        // 4) 回退：第一个变体
        return items[0].copyWithCount(1);
    }

    /**
     * 构建 ME 网络库存快照（AEItemKey → 存储量）。供 JEI "+" 模板挑选一次性调用。
     */
    public Map<AEItemKey, Long> snapshotStoredAmounts() {
        Map<AEItemKey, Long> map = new HashMap<>();
        var clientRepo = getClientRepo();
        if (clientRepo != null) {
            for (var entry : clientRepo.getAllEntries()) {
                if (!(entry.getWhat() instanceof AEItemKey itemKey)) continue;
                if (entry.getStoredAmount() > 0) {
                    map.put(itemKey, entry.getStoredAmount());
                }
            }
        }
        return map;
    }

    /**
     * 构建网络中可自动合成物品的集合快照。供 JEI "+" 模板挑选一次性调用。
     */
    public Set<AEItemKey> snapshotCraftableKeys() {
        Set<AEItemKey> set = new HashSet<>();
        var clientRepo = getClientRepo();
        if (clientRepo != null) {
            for (var entry : clientRepo.getAllEntries()) {
                if (!(entry.getWhat() instanceof AEItemKey itemKey)) continue;
                if (entry.isCraftable()) {
                    set.add(itemKey);
                }
            }
        }
        return set;
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
