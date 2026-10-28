package thaumicenergistics.common.inventory;

import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
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
        return this.patterns.size() < MAXIMUM_STORED_PATTERNS;
    }

    public boolean hasPatternFor(ItemStack resultStack) {
        return getPatternForItem(resultStack) != null;
    }

    public ArcaneCraftingPattern getPatternForItem(ItemStack resultStack) {
        for (ArcaneCraftingPattern p : this.patterns) {
            if (p != null && p.getResult() != null && !p.getResult().isEmpty()) {
                if (ItemStack.isSameItem(p.getResult(), resultStack)) {
                    return p;
                }
            }
        }
        return null;
    }

    public ArrayList<ArcaneCraftingPattern> getPatterns() {
        return this.patterns;
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

        var definition = pattern.writeToNBT(new CompoundTag());
        boolean exists = this.patterns.stream().anyMatch(existing ->
                existing.writeToNBT(new CompoundTag()).equals(definition));
        if (!exists) {
            this.patterns.add(pattern);
            saveKCoreData();
        }
    }

    public void removePattern(ArcaneCraftingPattern pattern) {
        if (this.patterns.remove(pattern)) {
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
        CompoundTag data = getOrCreateNBT();

        if (data.contains(NBTKEY_PATTERNS)) {
            ListTag plist = data.getList(NBTKEY_PATTERNS, Tag.TAG_COMPOUND);
            for (int i = 0; i < plist.size(); i++) {
                try {
                    ArcaneCraftingPattern pattern = new ArcaneCraftingPattern(plist.getCompound(i));
                    if (pattern.isPatternValid()) {
                        this.patterns.add(pattern);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveKCoreData() {
        CompoundTag data = getOrCreateNBT();
        ListTag plist = new ListTag();

        for (ArcaneCraftingPattern pattern : this.patterns) {
            if (pattern == null || !pattern.isPatternValid()) continue;
            plist.add(pattern.writeToNBT(new CompoundTag()));
        }

        if (!plist.isEmpty()) {
            data.put(NBTKEY_PATTERNS, plist);
        } else {
            data.remove(NBTKEY_PATTERNS);
        }

        setNBT(data);
    }
}
