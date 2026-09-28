package tc_energy.common.integration.tc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import dev.tc4port.thaumcraft.api.aspect.AspectId;

/**
 * 奥术合成样板，存储在知识核心中。
 * 从 1.7.10 ArcaneCraftingPattern 移植；Ren 版 aspects 用 Map<AspectId,Integer>，
 * NBT 沿用 {aspectSerialized: amount} 扁平结构（与旧 AspectList NBT 兼容层一致）。
 */
public class ArcaneCraftingPattern {
    private static final String NBTKEY_INGREDIENT_NUM = "input#";
    private static final String NBTKEY_RESULT = "output";
    private static final String NBTKEY_ASPECTS = "aspects";
    private static final String NBTKEY_RESEARCH = "research";
    private static final int GRID_SIZE = 9;

    /** 全局 RegistryAccess，用于 NBT 读写 */
    private static final HolderLookup.Provider REGISTRY_ACCESS =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    protected Map<AspectId, Integer> aspects = new LinkedHashMap<>();
    protected ItemStack[] ingredients = new ItemStack[GRID_SIZE];
    protected ItemStack result = ItemStack.EMPTY;
    protected boolean isValid;
    /** 对应 TC 奥术配方的研究键（用于使用端按玩家校验解锁）。可为空（老数据/无研究配方）。 */
    protected String research;

    public ArcaneCraftingPattern(Map<AspectId, Integer> aspects, ItemStack result, ItemStack[] ingredients) {
        this(aspects, result, ingredients, null);
    }

    public ArcaneCraftingPattern(Map<AspectId, Integer> aspects, ItemStack result, ItemStack[] ingredients, String research) {
        if (aspects != null) {
            this.aspects.putAll(aspects);
        }
        this.result = result.copy();
        this.research = research;

        boolean hasValidInput = false;
        for (int i = 0; i < GRID_SIZE && i < ingredients.length; i++) {
            if (ingredients[i] != null && !ingredients[i].isEmpty()) {
                this.ingredients[i] = ingredients[i].copy();
                hasValidInput = true;
            }
        }

        this.isValid = !this.aspects.isEmpty() && !this.result.isEmpty() && hasValidInput;
    }

    public ArcaneCraftingPattern(CompoundTag data) {
        readFromNBT(data);
    }

    public boolean isPatternValid() {
        return isValid;
    }

    public Map<AspectId, Integer> getAspects() {
        return aspects;
    }

    public ItemStack getResult() {
        return result;
    }

    public ItemStack[] getIngredients() {
        return ingredients;
    }

    public java.util.Set<AspectId> getCachedAspects() {
        return aspects.keySet();
    }

    public int getAspectCost(AspectId aspect) {
        return aspects.getOrDefault(aspect, 0);
    }

    /** 对应 TC 奥术配方的研究键；无研究要求/老数据时可能为 null。 */
    @org.jetbrains.annotations.Nullable
    public String getResearch() {
        return research;
    }

    @SuppressWarnings("unchecked")
    public static boolean canSubstituteFor(Object target, ItemStack input) {
        if (target instanceof ItemStack targetStack) {
            return ItemStack.isSameItemSameComponents(targetStack, input);
        } else if (target instanceof List) {
            List<ItemStack> items = (List<ItemStack>) target;
            for (ItemStack item : items) {
                if (ItemStack.isSameItemSameComponents(item, input)) return true;
            }
        }
        return false;
    }

    public void readFromNBT(CompoundTag data) {
        if (data == null) return;

        this.ingredients = new ItemStack[GRID_SIZE];
        this.aspects = new LinkedHashMap<>();
        this.isValid = true;

        if (data.contains(NBTKEY_RESEARCH)) {
            this.research = data.getString(NBTKEY_RESEARCH);
        }

        if (data.contains(NBTKEY_ASPECTS)) {
            CompoundTag aspectTag = data.getCompound(NBTKEY_ASPECTS);
            for (String key : aspectTag.getAllKeys()) {
                int amount = aspectTag.getInt(key);
                if (amount > 0) {
                    try {
                        this.aspects.put(AspectId.parse(key), amount);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        if (this.aspects.isEmpty()) {
            this.isValid = false;
            return;
        }

        for (int slot = 0; slot < GRID_SIZE; slot++) {
            if (!data.contains(NBTKEY_INGREDIENT_NUM + slot)) continue;

            CompoundTag ingTag = data.getCompound(NBTKEY_INGREDIENT_NUM + slot);
            if (ingTag.isEmpty()) {
                this.isValid = false;
                return;
            }

            ItemStack stack = ItemStack.parseOptional(REGISTRY_ACCESS, ingTag);
            if (stack.isEmpty()) {
                this.isValid = false;
                return;
            }
            this.ingredients[slot] = stack;
        }

        if (data.contains(NBTKEY_RESULT)) {
            CompoundTag resultTag = data.getCompound(NBTKEY_RESULT);
            this.result = ItemStack.parseOptional(REGISTRY_ACCESS, resultTag);
            if (this.result.isEmpty()) {
                this.isValid = false;
            }
        } else {
            this.isValid = false;
        }
    }

    public CompoundTag writeToNBT(CompoundTag data) {
        CompoundTag aspectTag = new CompoundTag();
        this.aspects.forEach((aspect, amount) -> aspectTag.putInt(aspect.serialized(), amount));
        data.put(NBTKEY_ASPECTS, aspectTag);

        if (this.research != null && !this.research.isEmpty()) {
            data.putString(NBTKEY_RESEARCH, this.research);
        }

        for (int i = 0; i < GRID_SIZE; i++) {
            if (this.ingredients[i] == null || this.ingredients[i].isEmpty()) continue;
            // 1.21.1: ItemStack.save(Provider) 返回新的 Tag，不会写入传入的 CompoundTag。
            // 必须用返回值（之前用双参 save 并丢弃返回值，导致存了空 tag、配方反序列化全失败）。
            CompoundTag ingTag = (CompoundTag) this.ingredients[i].save(REGISTRY_ACCESS);
            data.put(NBTKEY_INGREDIENT_NUM + i, ingTag);
        }

        CompoundTag resultTag = (CompoundTag) this.result.save(REGISTRY_ACCESS);
        data.put(NBTKEY_RESULT, resultTag);

        return data;
    }
}
