package tc_energy.common.container;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingApi;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingRecipe;
import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import tc_energy.common.container.slot.GhostSlot;
import tc_energy.common.container.slot.ReadOnlySlot;
import tc_energy.common.integration.tc.ArcaneCraftingPattern;
import tc_energy.common.inventory.HandlerKnowledgeCore;
import tc_energy.common.network.KnowledgeInscriberGhostSlotPacket;
import tc_energy.common.tiles.TileKnowledgeInscriber;
import tc_energy.init.ModItems;
import tc_energy.init.ModMenuTypes;
import net.neoforged.neoforge.network.PacketDistributor;

public class ContainerKnowledgeInscriber extends AbstractContainerMenu {

    public enum CoreSaveState {
        Disabled_InvalidRecipe,
        Disabled_CoreFull,
        Disabled_MissingCore,
        Enabled_Save,
        Enabled_Delete
    }

    private static final int MAXIMUM_PATTERNS = HandlerKnowledgeCore.MAXIMUM_STORED_PATTERNS;

    private static final int PLAYER_INV_Y = 160;
    private static final int HOTBAR_Y = 218;
    private static final int PLAYER_INV_X = 8;

    private static final int KCORE_SLOT_X = 186;
    private static final int KCORE_SLOT_Y = 8;

    private static final int PATTERN_SLOT_X = 26;
    private static final int PATTERN_SLOT_Y = 18;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COLS = 7;
    private static final int PATTERN_SPACING = 18;

    private static final int CRAFTING_SLOT_X = 26;
    private static final int CRAFTING_SLOT_Y = 90;
    private static final int CRAFTING_ROWS = 3;
    private static final int CRAFTING_COLS = 3;
    private static final int CRAFTING_GRID_SIZE = 9;
    private static final int CRAFTING_SPACING = 18;

    private static final int RESULT_SLOT_X = 116;
    private static final int RESULT_SLOT_Y = 108;

    private final HandlerKnowledgeCore kCoreHandler = new HandlerKnowledgeCore();
    private final TileKnowledgeInscriber tile;
    private final Inventory playerInv;

    private final ItemStackHandler kCoreInventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.KNOWLEDGE_CORE.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            onKCoreChanged();
        }
    };

    private final ItemStackHandler patternInventory = new ItemStackHandler(MAXIMUM_PATTERNS);
    private final ItemStackHandler craftingInventory = new ItemStackHandler(CRAFTING_GRID_SIZE);
    private final ItemStackHandler resultInventory = new ItemStackHandler(1);

    private final SimpleContainer craftingContainer = new SimpleContainer(CRAFTING_GRID_SIZE);

    private RecipeHolder activeRecipe = null;

    /** 轻量包装：RecipeHolder + 预读的 research/vis（保存样板用）。 */
    public record RecipeHolder(net.minecraft.world.item.crafting.RecipeHolder<ArcaneCraftingRecipe> holder, String research, java.util.Map<VisChannel, Integer> vis) {}
    private final DataSlot saveStateSlot;

    public ContainerKnowledgeInscriber(int id, Inventory inv) {
        this(id, inv, (TileKnowledgeInscriber) null);
    }

    public ContainerKnowledgeInscriber(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, (TileKnowledgeInscriber) null);
    }

    public ContainerKnowledgeInscriber(int id, Inventory inv, TileKnowledgeInscriber tile) {
        super(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), id);
        this.tile = tile;
        this.playerInv = inv;

        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, PLAYER_INV_X + c * 18, HOTBAR_Y));

        addSlot(new SlotItemHandler(kCoreInventory, 0, KCORE_SLOT_X, KCORE_SLOT_Y) {
            @Override
            public int getMaxStackSize() { return 1; }
        });

        initPatternSlots();
        initCraftingSlots();

        addSlot(new ReadOnlySlot(resultInventory, 0, RESULT_SLOT_X, RESULT_SLOT_Y));

        saveStateSlot = addDataSlot(DataSlot.standalone());
        saveStateSlot.set(0);

        if (tile != null && tile.hasKCore()) {
            ItemStack slotStack = tile.getKCore().copy();
            kCoreInventory.setStackInSlot(0, slotStack);
            kCoreHandler.open(slotStack);
            updatePatternSlots();
        }
        // 服务端构造后立即同步按钮状态到客户端（否则客户端 saveStateSlot 一直为初始 0 → 按钮永远无效）
        sendSaveState();
    }

    private void initPatternSlots() {
        for (int r = 0; r < PATTERN_ROWS; r++)
            for (int c = 0; c < PATTERN_COLS; c++) {
                int idx = r * PATTERN_COLS + c;
                int x = PATTERN_SLOT_X + c * PATTERN_SPACING;
                int y = PATTERN_SLOT_Y + r * PATTERN_SPACING;
                addSlot(new SlotItemHandler(patternInventory, idx, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack s) { return false; }
                    @Override
                    public boolean mayPickup(Player p) { return false; }
                    @Override
                    public int getMaxStackSize() { return 1; }
                });
            }
    }

    private void initCraftingSlots() {
        for (int r = 0; r < CRAFTING_ROWS; r++)
            for (int c = 0; c < CRAFTING_COLS; c++) {
                int idx = r * CRAFTING_COLS + c;
                int x = CRAFTING_SLOT_X + c * CRAFTING_SPACING;
                int y = CRAFTING_SLOT_Y + r * CRAFTING_SPACING;
                addSlot(new GhostSlot(craftingContainer, idx, x, y));
            }
    }

    private void syncKCoreToTile() {
        if (tile != null) {
            ItemStack kCore = kCoreInventory.getStackInSlot(0);
            tile.inventory.setStackInSlot(TileKnowledgeInscriber.KCORE_SLOT, kCore.copy());
            tile.setChanged();
        }
    }

    private void onKCoreChanged() {
        ItemStack kCore = kCoreInventory.getStackInSlot(0);
        if (kCore.isEmpty()) {
            kCoreHandler.close();
            clearPatternSlots();
        } else if (!kCoreHandler.isHandlingCore(kCore)) {
            kCoreHandler.open(kCore);
            updatePatternSlots();
        }

        syncKCoreToTile();
        // 放/取核心后同步按钮状态（可保存/可删除/无效）到客户端
        sendSaveState();
    }

    private void clearPatternSlots() {
        for (int i = 0; i < MAXIMUM_PATTERNS; i++) {
            patternInventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    private void updatePatternSlots() {
        ArrayList<ItemStack> results = kCoreHandler.hasCore()
                ? kCoreHandler.getStoredOutputs()
                : new ArrayList<>();
        Iterator<ItemStack> it = results.iterator();

        for (int i = 0; i < MAXIMUM_PATTERNS; i++) {
            if (it.hasNext()) {
                patternInventory.setStackInSlot(i, it.next().copy());
            } else {
                patternInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private CoreSaveState getSaveState() {
        if (!kCoreHandler.hasCore()) {
            return CoreSaveState.Disabled_MissingCore;
        }
        if (activeRecipe == null) {
            return CoreSaveState.Disabled_InvalidRecipe;
        }

        ItemStack result = resultInventory.getStackInSlot(0);
        if (result.isEmpty()) {
            return CoreSaveState.Disabled_InvalidRecipe;
        }

        // 研究限制：配方有研究要求且玩家未解锁 → 禁止保存
        String research = activeRecipe.research();
        if (research != null && !research.isEmpty()
                && !ResearchApi.isComplete(playerInv.player, ResearchKey.parse(research))) {
            return CoreSaveState.Disabled_InvalidRecipe;
        }

        boolean isNew = !kCoreHandler.hasPatternFor(result);
        if (isNew) {
            return kCoreHandler.hasRoomToStorePattern()
                    ? CoreSaveState.Enabled_Save
                    : CoreSaveState.Disabled_CoreFull;
        } else {
            return CoreSaveState.Enabled_Delete;
        }
    }

    private void updateRecipe() {
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            craftingContainer.setItem(i, craftingInventory.getStackInSlot(i).isEmpty()
                    ? ItemStack.EMPTY
                    : craftingInventory.getStackInSlot(i).copy());
        }

        activeRecipe = findArcaneRecipe();

        if (activeRecipe != null) {
            var input = toCraftingInput();
            ItemStack output = activeRecipe.holder().value().assemble(input, playerInv.player.level().registryAccess());
            resultInventory.setStackInSlot(0, output);
        } else {
            resultInventory.setStackInSlot(0, ItemStack.EMPTY);
        }

        sendSaveState();
    }

    private net.minecraft.world.item.crafting.CraftingInput toCraftingInput() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(CRAFTING_GRID_SIZE);
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            items.add(craftingInventory.getStackInSlot(i));
        }
        return net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
    }

    private RecipeHolder findArcaneRecipe() {
        Level level = playerInv.player.level();
        if (level == null) {
            return null;
        }
        var input = toCraftingInput();
        for (var holder : ArcaneCraftingApi.recipes(level)) {
            ArcaneCraftingRecipe recipe = holder.value();
            try {
                if (recipe.matches(input, level)) {
                    String research = recipe.research().key()
                            .map(ResearchKey::serialized).orElse(null);
                    // 动态配方（法杖组装）按实际合成格取 vis，否则编码出的样板 vis 花费为 0
                    return new RecipeHolder(holder, research, recipe.vis(input));
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void sendSaveState() {
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide) {
            CoreSaveState s = getSaveState();
            saveStateSlot.set(s.ordinal());
        }
    }

    public void onSaveDelete() {
        CoreSaveState state = getSaveState();
        if (state == CoreSaveState.Enabled_Save) {
            savePattern();
        } else if (state == CoreSaveState.Enabled_Delete) {
            deletePattern();
        }
    }

    private void savePattern() {
        if (activeRecipe == null) return;

        // 研究限制：配方有研究要求时，操作者必须已解锁对应研究才能编码
        String research = activeRecipe.research();
        if (research != null && !research.isEmpty()
                && !ResearchApi.isComplete(playerInv.player, ResearchKey.parse(research))) {
            return;
        }

        ItemStack[] inputs = new ItemStack[CRAFTING_GRID_SIZE];
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            ItemStack stack = craftingInventory.getStackInSlot(i);
            inputs[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        java.util.Map<dev.tc4port.thaumcraft.api.aspect.AspectId, Integer> aspects = new java.util.LinkedHashMap<>();
        activeRecipe.vis().forEach((channel, whole) -> {
            if (whole != null && whole > 0) {
                aspects.put(channel.aspectId(), whole);
            }
        });
        ItemStack result = resultInventory.getStackInSlot(0).copy();

        ArcaneCraftingPattern pattern = new ArcaneCraftingPattern(aspects, result, inputs, research);
        kCoreHandler.addPattern(pattern);
        updatePatternSlots();
        sendSaveState();
        syncKCoreToTile();
    }

    private void deletePattern() {
        ItemStack result = resultInventory.getStackInSlot(0);
        ArcaneCraftingPattern pattern = kCoreHandler.getPatternForItem(result);
        if (pattern != null) {
            kCoreHandler.removePattern(pattern);
            updatePatternSlots();
            sendSaveState();
            syncKCoreToTile();
        }
    }

    public void onClearGrid() {
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            craftingInventory.setStackInSlot(i, ItemStack.EMPTY);
            craftingContainer.setItem(i, ItemStack.EMPTY);
        }
        activeRecipe = null;
        resultInventory.setStackInSlot(0, ItemStack.EMPTY);
        sendSaveState();
    }

    public CoreSaveState getCurrentSaveState() {
        return CoreSaveState.values()[saveStateSlot.get()];
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            // 图案槽（核心已有配方，slotId 37..37+MAXIMUM_PATTERNS-1）：点击加载该配方到合成网格 → 可保存/删除
            if (slotId >= 37 && slotId < 37 + MAXIMUM_PATTERNS) {
                int patternIdx = slotId - 37;
                ItemStack output = patternInventory.getStackInSlot(patternIdx);
                if (!output.isEmpty()) {
                    ArcaneCraftingPattern pattern = kCoreHandler.getPatternForItem(output);
                    if (pattern != null) {
                        loadPatternToGrid(pattern, player);
                    }
                }
                return;
            }
            if (slot instanceof GhostSlot ghostSlot) {
                int gridIndex = ghostSlot.index;
                ItemStack carried = getCarried();
                ItemStack newStack = carried.isEmpty()
                        ? ItemStack.EMPTY
                        : carried.copyWithCount(1);

                ghostSlot.set(newStack);

                if (player.level().isClientSide) {
                    onCraftingChangedClient();
                    PacketDistributor.sendToServer(
                            new KnowledgeInscriberGhostSlotPacket(containerId, gridIndex, newStack));
                } else {
                    onCraftingChanged();
                }
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    public void setGhostSlotOnServer(int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || slotIndex >= CRAFTING_GRID_SIZE) return;
        ItemStack s = stack == null ? ItemStack.EMPTY : stack.copy();
        // 两个容器都要写：craftingContainer 供 Slot 渲染/点击，craftingInventory 供配方匹配（updateRecipe 读它）。
        // 之前只写 craftingContainer 导致 JEI 转移后 updateRecipe 读到空网格 → 结果槽永远为空。
        craftingContainer.setItem(slotIndex, s);
        craftingInventory.setStackInSlot(slotIndex, s.copy());
        int slotId = 37 + MAXIMUM_PATTERNS + slotIndex;
        if (slotId < slots.size() && slots.get(slotId) instanceof GhostSlot) {
            slots.get(slotId).set(s);
        }
        onCraftingChanged();
    }

    /**
     * GhostSlot 对应的合成网格索引（0-8）。
     * <p>
     * ⚠ {@code GhostSlot} 是用 {@code addSlot(new GhostSlot(craftingContainer, idx, x, y))} 注册的，
     * 其 {@link Slot#index} <b>就是</b>网格索引 idx（0-8），不是菜单槽序号 ——
     * 不要再去减 {@code 37 + MAXIMUM_PATTERNS}（那会得到负数，导致 JEI 拖拽被
     * {@link #setGhostSlotOnServer} 的 {@code slotIndex < 0} 守卫静默丢弃）。
     * 手动的 {@link #clicked} 分支也是直接取 {@code ghostSlot.index}，此处保持一致。
     */
    public int getGhostGridIndex(Slot slot) {
        if (slot instanceof GhostSlot && slot.container == craftingContainer
                && slot.index >= 0 && slot.index < CRAFTING_GRID_SIZE) {
            return slot.index;
        }
        return -1;
    }

    /** 点击图案槽：把核心已有配方加载到合成网格（清空 + 填 9 格材料），供保存/删除。 */
    private void loadPatternToGrid(ArcaneCraftingPattern pattern, Player player) {
        ItemStack[] ingredients = pattern.getIngredients();
        boolean client = player.level().isClientSide;
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            ItemStack s = i < ingredients.length && ingredients[i] != null ? ingredients[i].copy() : ItemStack.EMPTY;
            craftingContainer.setItem(i, s.copy());
            int slotId = 37 + MAXIMUM_PATTERNS + i;
            if (slotId < slots.size() && slots.get(slotId) instanceof GhostSlot gs) {
                gs.set(s.copy());
            }
            if (client) {
                PacketDistributor.sendToServer(new KnowledgeInscriberGhostSlotPacket(containerId, i, s.copy()));
            }
        }
        if (client) {
            onCraftingChangedClient();
        } else {
            onCraftingChanged();
        }
    }

    public void onCraftingChangedClient() {
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            craftingInventory.setStackInSlot(i, craftingContainer.getItem(i).copy());
        }
        updateRecipe();
    }

    private void onCraftingChanged() {
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            craftingInventory.setStackInSlot(i, craftingContainer.getItem(i).copy());
        }
        updateRecipe();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            onSaveDelete();
            return true;
        } else if (id == 1) {
            onClearGrid();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (idx < 36) {
            if (stack.is(ModItems.KNOWLEDGE_CORE.get())) {
                if (!moveItemStackTo(stack, 36, 37, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        } else if (idx == 36) {
            if (!moveItemStackTo(stack, 0, 36, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        syncKCoreToTile();
        kCoreHandler.close();
    }
}
