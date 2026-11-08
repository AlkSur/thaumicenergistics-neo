package thaumicenergistics.common.inventory;

import java.util.ArrayList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import thaumicenergistics.common.integration.tc.ArcaneCraftingPattern;
import thaumicenergistics.init.ModItems;

/**
 * 管理知识核心中存储的奥术合成样板。
 * 从 1.7.10 HandlerKnowledgeCore 移植。
 */
public class HandlerKnowledgeCore {
    private static final String NBTKEY_PATTERNS = "Patterns";
    public static final int MAXIMUM_STORED_PATTERNS = 21;

    private final ArrayList<ArcaneCraftingPattern> patterns = new ArrayList<>(MAXIMUM_STORED_PATTERNS);
    // Retain the exact serialized entry, including unknown fields and undecodable patterns.
    private final ArrayList<StoredPattern> storedPatterns = new ArrayList<>();
    private boolean editable = true;
    private record StoredPattern(CompoundTag original, ArcaneCraftingPattern decoded) {}
    private ItemStack kCore;

    public HandlerKnowledgeCore() {}

    public HandlerKnowledgeCore(ItemStack kCore) {
        open(kCore);
    }

    public void open(ItemStack kCore) {
        close();
        this.kCore = kCore;
        loadKCoreData();
    }

    public void close() {
        this.kCore = null;
        this.patterns.clear();
        this.storedPatterns.clear();
        this.editable = true;
    }

    public boolean hasCore() {
        return this.kCore != null;
    }

    public boolean isHandlingCore(ItemStack kCore) {
        if (this.kCore == null) return false;
        if (kCore == null || kCore.isEmpty()) return false;
        if (!kCore.is(ModItems.KNOWLEDGE_CORE.get())) return false;
        return ItemStack.matches(this.kCore, kCore);
    }

    public boolean hasRoomToStorePattern() {
        return currentEntriesMatch() && this.storedPatterns.size() < MAXIMUM_STORED_PATTERNS;
    }

    public boolean hasPatternFor(ItemStack resultStack) {
        return getPatternForItem(resultStack) != null;
    }

    public ArcaneCraftingPattern getPatternForItem(ItemStack resultStack) {
        for (ArcaneCraftingPattern p : this.patterns) {
            if (p != null && p.getResult() != null && !p.getResult().isEmpty()) {
                if (ItemStack.isSameItemSameComponents(p.getResult(), resultStack)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Match the complete definition, never just the displayed output. */
    public ArcaneCraftingPattern getMatchingPattern(ArcaneCraftingPattern candidate) {
        if (candidate == null || !candidate.isPatternValid()) return null;
        CompoundTag definition = candidate.writeToNBT(new CompoundTag());
        for (ArcaneCraftingPattern stored : this.patterns) {
            if (stored.writeToNBT(new CompoundTag()).equals(definition)) return stored;
        }
        return null;
    }

    public ArrayList<ArcaneCraftingPattern> getPatterns() {
        return new ArrayList<>(this.patterns);
    }

    /** Editor indices include opaque entries; crafting consumers still see only decoded patterns. */
    public int getStoredEntryCount() {
        return storedPatterns.size();
    }

    public CompoundTag getStoredEntry(int index) {
        return storedPatterns.get(index).original().copy();
    }

    public boolean matchesStoredEntry(int index, CompoundTag expected) {
        return expected != null && index >= 0 && index < storedPatterns.size()
                && storedPatterns.get(index).original().equals(expected)
                && currentEntriesMatch();
    }

    public boolean removeStoredEntry(int index, CompoundTag expected) {
        if (!matchesStoredEntry(index, expected)) return false;
        StoredPattern removed = storedPatterns.remove(index);
        if (removed.decoded() != null) patterns.remove(removed.decoded());
        saveKCoreData();
        return true;
    }

    /** Best-effort display only. A missing item never hides the record or makes it executable. */
    public ItemStack getStoredEntryOutput(int index) {
        ItemStack output = readDisplayStack(storedPatterns.get(index).original(), "output");
        if (!output.isEmpty()) return output;
        ItemStack placeholder = new ItemStack(Items.BARRIER);
        placeholder.set(DataComponents.CUSTOM_NAME, Component.translatable(
                "thaumicenergistics.gui.knowledge_inscriber.unreadable_pattern"));
        return placeholder;
    }

    public ItemStack[] getStoredEntryInputs(int index) {
        CompoundTag original = storedPatterns.get(index).original();
        ItemStack[] inputs = new ItemStack[9];
        for (int i = 0; i < inputs.length; i++) inputs[i] = readDisplayStack(original, "input#" + i);
        return inputs;
    }

    private static ItemStack readDisplayStack(CompoundTag original, String key) {
        try {
            return ItemStack.parseOptional(RegistryAccess.fromRegistryOfRegistries(
                    BuiltInRegistries.REGISTRY), original.getCompound(key));
        } catch (RuntimeException invalid) {
            return ItemStack.EMPTY;
        }
    }

    public ArrayList<ItemStack> getStoredOutputs() {
        ArrayList<ItemStack> results = new ArrayList<>();
        for (ArcaneCraftingPattern p : this.patterns) {
            if (p != null && p.getResult() != null && !p.getResult().isEmpty()) {
                results.add(p.getResult().copy());
            }
        }
        return results;
    }

    public void addPattern(ArcaneCraftingPattern pattern) {
        if (pattern == null || !pattern.isPatternValid()) return;
        if (!hasRoomToStorePattern()) return;

        // Full definition includes the input components, costs, research and output components.
        // Equal outputs alone do not imply the same recipe.
        CompoundTag encoded = pattern.writeToNBT(new CompoundTag());
        boolean exists = this.patterns.stream().anyMatch(existing ->
                existing.writeToNBT(new CompoundTag()).equals(encoded));
        if (!exists) {
            this.patterns.add(pattern);
            this.storedPatterns.add(new StoredPattern(encoded, pattern));
            saveKCoreData();
        }
    }

    public void removePattern(ArcaneCraftingPattern pattern) {
        if (currentEntriesMatch() && this.patterns.remove(pattern)) {
            this.storedPatterns.removeIf(entry -> entry.decoded() == pattern);
            saveKCoreData();
        }
    }

    private CompoundTag getOrCreateNBT() {
        CustomData customData = this.kCore.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return new CompoundTag();
        }
        return customData.copyTag();
    }

    private void setNBT(CompoundTag tag) {
        this.kCore.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private void loadKCoreData() {
        this.patterns.clear();
        this.storedPatterns.clear();
        this.editable = true;
        CompoundTag data = getOrCreateNBT();

        if (data.contains(NBTKEY_PATTERNS)) {
            Tag encoded = data.get(NBTKEY_PATTERNS);
            if (!(encoded instanceof ListTag plist)
                    || (!plist.isEmpty() && plist.getElementType() != Tag.TAG_COMPOUND)) {
                // Unknown root format is opaque. Reject edits rather than replacing it with an empty list.
                this.editable = false;
                return;
            }
            for (int i = 0; i < plist.size(); i++) {
                CompoundTag original = plist.getCompound(i).copy();
                ArcaneCraftingPattern decoded = null;
                try {
                    ArcaneCraftingPattern pattern = new ArcaneCraftingPattern(original.copy());
                    if (pattern.isPatternValid()) {
                        this.patterns.add(pattern);
                        decoded = pattern;
                    }
                } catch (RuntimeException exception) {
                    // Only this entry is unusable; its raw data is retained alongside healthy entries.
                    thaumicenergistics.ThaumicEnergistics.LOG.warn("Cannot decode knowledge core pattern at index {}; retaining raw data", i, exception);
                }
                this.storedPatterns.add(new StoredPattern(original, decoded));
            }
        }
    }

    private ListTag encodedEntries() {
        ListTag list = new ListTag();
        for (StoredPattern stored : storedPatterns) list.add(stored.original().copy());
        return list;
    }

    private boolean currentEntriesMatch() {
        if (!editable || !hasCore()) return false;
        var data = getOrCreateNBT();
        var current = data.get(NBTKEY_PATTERNS);
        return current == null ? storedPatterns.isEmpty()
                : current instanceof ListTag && current.equals(encodedEntries());
    }

    private void saveKCoreData() {
        if (!editable) return;
        CompoundTag data = getOrCreateNBT();
        ListTag plist = encodedEntries();

        if (!plist.isEmpty()) {
            data.put(NBTKEY_PATTERNS, plist);
        } else {
            data.remove(NBTKEY_PATTERNS);
        }

        setNBT(data);
    }
}
