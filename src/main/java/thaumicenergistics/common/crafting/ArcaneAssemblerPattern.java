package thaumicenergistics.common.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.aspects.AspectList;

public final class ArcaneAssemblerPattern implements IPatternDetails {
    private static final net.minecraft.resources.ResourceLocation PRIMORDIAL_PEARL_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "primordial_pearl");
    private final AEItemKey definition;
    private final IArcaneRecipe recipe;
    private final AspectList aspects;
    private final ItemStack resultStack;
    private final IInput[] inputs;
    private final String research;
    /** 3×3 配方网格（知识核心存储的 9 格材料，GUI 配方预览用）。 */
    private final ItemStack[] grid;

    public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                  AspectList aspects, ItemStack resultStack,
                                  List<GenericStack> inputStacks) {
        this(definition, recipe, aspects, resultStack, inputStacks, null, null, null);
    }

    public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                  AspectList aspects, ItemStack resultStack,
                                  List<GenericStack> inputStacks, String research) {
        this(definition, recipe, aspects, resultStack, inputStacks, research, null, null);
    }

    public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                  AspectList aspects, ItemStack resultStack,
                                  List<GenericStack> inputStacks, String research, ItemStack[] grid) {
        this(definition, recipe, aspects, resultStack, inputStacks, research, grid, null);
    }

    public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                  AspectList aspects, ItemStack resultStack,
                                  List<GenericStack> inputStacks, String research, ItemStack[] grid,
                                  List<GenericStack[]> inputAlternatives) {
        this.definition = definition;
        this.recipe = recipe;
        this.aspects = aspects;
        this.resultStack = resultStack;
        this.research = research;
        this.grid = grid;
        this.inputs = new IInput[inputStacks.size()];
        for (int i = 0; i < inputStacks.size(); i++) {
            GenericStack stack = inputStacks.get(i);
            // 通配变体组（tag 展开结果）；null/单元素退化为精确匹配（与旧行为一致）
            GenericStack[] alts = (inputAlternatives != null && i < inputAlternatives.size()
                    && inputAlternatives.get(i) != null)
                    ? inputAlternatives.get(i)
                    : new GenericStack[]{stack};
            this.inputs[i] = new IInput() {
                @Override
                public GenericStack[] getPossibleInputs() {
                    return alts;
                }

                @Override
                public long getMultiplier() {
                    return 1;
                }

                @Override
                public boolean isValid(AEKey input, Level level) {
                    if (!(input instanceof AEItemKey itemKey)) return false;
                    for (GenericStack alt : alts) {
                        if (alt.what() instanceof AEItemKey altKey && altKey.equals(itemKey)) {
                            return true;
                        }
                    }
                    return false;
                }

                @Nullable
                @Override
                public AEKey getRemainingKey(AEKey what) {
                    // 元始珍珠作为"催化"原料：声明合成后它会返回网络（AE2 原生容器机制）。
                    // 规划端据此按"每次 1 单"顺序模拟、执行端把珍珠计入 CPU waitingFor，
                    // 机器合成完成把珍珠退回网络后 CPU 自动捕获续下一单——1 颗珍珠可连续多单。
                    if (what instanceof AEItemKey itemKey
                            && itemKey.getItem() == BuiltInRegistries.ITEM.get(PRIMORDIAL_PEARL_ID)) {
                        return what;
                    }
                    return null;
                }
            };
        }
    }

    @Override
    public AEItemKey getDefinition() { return definition; }

    @Override
    public IInput[] getInputs() { return inputs; }

    @Override
    public List<GenericStack> getOutputs() {
        return List.of(new GenericStack(AEItemKey.of(resultStack), resultStack.getCount()));
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArcaneAssemblerPattern other)) return false;
        return definition != null && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition != null ? definition.hashCode() : 0;
    }

    public IArcaneRecipe getRecipe() { return recipe; }
    public AspectList getAspects() { return aspects; }
    public ItemStack getResultStack() { return resultStack; }
    /** 3×3 配方网格（可能为 null/短数组，GUI 预览用）。 */
    @Nullable
    public ItemStack[] getGrid() { return grid; }
    /** 对应 TC 奥术配方的研究键；无研究要求/老数据时可能为 null。 */
    @Nullable
    public String getResearch() { return research; }

    /** 在接收任务前校验 CPU 的真实材料；失败时保持所有输入不变。 */
    @Nullable
    public ItemStack[] materializeInputs(KeyCounter[] holders, Level level) {
        if (grid == null || holders.length != inputs.length) return null;
        ItemStack[] actual = new ItemStack[9];
        java.util.Arrays.fill(actual, ItemStack.EMPTY);
        int inputIndex = 0;
        for (int slot = 0; slot < grid.length && slot < actual.length; slot++) {
            if (grid[slot] == null || grid[slot].isEmpty()) continue;
            if (inputIndex >= inputs.length) return null;
            IInput input = inputs[inputIndex];
            ItemStack selected = ItemStack.EMPTY;
            for (var entry : holders[inputIndex]) {
                if (entry.getLongValue() <= 0) continue;
                if (!selected.isEmpty() || !(entry.getKey() instanceof AEItemKey key)
                        || !input.isValid(key, level) || entry.getLongValue() != grid[slot].getCount()) {
                    return null;
                }
                selected = key.toStack(grid[slot].getCount());
            }
            if (selected.isEmpty()) return null;
            actual[slot] = selected;
            inputIndex++;
        }
        return inputIndex == inputs.length ? actual : null;
    }
}
