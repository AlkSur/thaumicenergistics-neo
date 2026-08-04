package thaumicenergistics.common.container;

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
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumicenergistics.common.container.slot.GhostSlot;
import thaumicenergistics.common.integration.tc.ArcaneCraftingPattern;
import thaumicenergistics.common.inventory.HandlerKnowledgeCore;
import thaumicenergistics.common.network.KnowledgeInscriberGhostSlotPacket;
import thaumicenergistics.common.tiles.TileKnowledgeInscriber;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.ThaumicEnergistics;

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

    private IArcaneRecipe activeRecipe = null;
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

        addSlot(new SlotItemHandler(resultInventory, 0, RESULT_SLOT_X, RESULT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack s) { return false; }
            @Override
            public boolean mayPickup(Player p) { return false; }
        });

        saveStateSlot = addDataSlot(DataSlot.standalone());
        saveStateSlot.set(0);

        if (tile != null && tile.hasKCore()) {
            ItemStack slotStack = tile.getKCore().copy();
            kCoreInventory.setStackInSlot(0, slotStack);
            kCoreHandler.open(slotStack);
            updatePatternSlots();
        }
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
            ItemStack output = activeRecipe.getCraftingResult(craftingContainer);
            resultInventory.setStackInSlot(0, output);
        } else {
            resultInventory.setStackInSlot(0, ItemStack.EMPTY);
        }

        sendSaveState();
    }

    private IArcaneRecipe findArcaneRecipe() {
        Level level = playerInv.player.level();
        if (level == null) {
            return null;
        }
        var recipes = ThaumcraftApi.getCraftingRecipes();
        int arcaneCount = 0;
        for (Object recipe : recipes) {
            if (recipe instanceof IArcaneRecipe arcaneRecipe) {
                arcaneCount++;
                try {
                    if (gridMatches(arcaneRecipe, craftingContainer)) {
                        return arcaneRecipe;
                    }
                } catch (Exception e) {
                }
            }
        }
        return null;
    }

    private boolean gridMatches(IArcaneRecipe recipe, SimpleContainer inv) {
        if (recipe instanceof ShapedArcaneRecipe shaped) {
            for (int x = 0; x <= 3 - shaped.width; x++) {
                for (int y = 0; y <= 3 - shaped.height; y++) {
                    if (checkShaped(shaped, inv, x, y, false) || checkShaped(shaped, inv, x, y, true)) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (recipe instanceof ShapelessArcaneRecipe shapeless) {
            ArrayList<Object> required = new ArrayList<>(shapeless.getInput());
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty()) continue;
                Iterator<Object> it = required.iterator();
                boolean inRecipe = false;
                while (it.hasNext()) {
                    Object next = it.next();
                    if (ingredientMatches(next, stack)) {
                        inRecipe = true;
                        it.remove();
                        break;
                    }
                }
                if (!inRecipe) return false;
            }
            return required.isEmpty();
        }
        return recipe.matches(inv, playerInv.player.level(), playerInv.player);
    }

    private boolean checkShaped(ShapedArcaneRecipe recipe, SimpleContainer inv, int startX, int startY, boolean mirror) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                int subX = x - startX;
                int subY = y - startY;
                Object target = null;
                if (subX >= 0 && subY >= 0 && subX < recipe.width && subY < recipe.height) {
                    target = mirror
                            ? recipe.input[recipe.width - subX - 1 + subY * recipe.width]
                            : recipe.input[subX + subY * recipe.width];
                }
                if (ingredientMatches(target, inv.getItem(x + y * 3))) continue;
                return false;
            }
        }
        return true;
    }

    private static boolean ingredientMatches(Object target, ItemStack input) {
        if (target == null) return input.isEmpty();
        if (target instanceof ItemStack t) return stackMatches(t, input);
        if (target instanceof List<?> list) {
            for (Object alt : list) {
                if (alt instanceof ItemStack s && stackMatches(s, input)) return true;
            }
            return false;
        }
        if (target instanceof TagKey<?> tag && tag.isFor(Registries.ITEM)) {
            @SuppressWarnings("unchecked")
            TagKey<Item> itemTag = (TagKey<Item>) tag;
            return !input.isEmpty() && input.is(itemTag);
        }
        return false;
    }

    private static boolean stackMatches(ItemStack target, ItemStack input) {
        if (input.isEmpty()) return target.isEmpty();
        if (target.isEmpty()) return false;
        return target.getItem() == input.getItem()
                && (target.getComponentsPatch().isEmpty() || ItemStack.isSameItemSameComponents(target, input));
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

        ItemStack[] inputs = new ItemStack[CRAFTING_GRID_SIZE];
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            ItemStack stack = craftingInventory.getStackInSlot(i);
            inputs[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        AspectList aspects = activeRecipe.getAspects(craftingContainer);
        if (aspects == null) {
            aspects = activeRecipe.getAspects();
        }
        ItemStack result = resultInventory.getStackInSlot(0).copy();

        ArcaneCraftingPattern pattern = new ArcaneCraftingPattern(aspects, result, inputs);
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
        craftingContainer.setItem(slotIndex, s);
        int slotId = 37 + MAXIMUM_PATTERNS + slotIndex;
        if (slotId < slots.size() && slots.get(slotId) instanceof GhostSlot) {
            slots.get(slotId).set(s);
        }
        onCraftingChanged();
    }

    /** GhostSlot 的网格索引（0-8）。ghostSlot.index 是菜单槽序号（37+MAXIMUM_PATTERNS+idx），需换算。 */
    public int getGhostGridIndex(Slot slot) {
        if (slot instanceof GhostSlot && slot.container == craftingContainer) {
            return slot.index - (37 + MAXIMUM_PATTERNS);
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
