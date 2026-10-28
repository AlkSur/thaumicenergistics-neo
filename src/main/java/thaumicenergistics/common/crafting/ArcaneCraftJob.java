package thaumicenergistics.common.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;

/** 装配器当前订单拥有的材料、产物和执行阶段；沿用原存档键。 */
public final class ArcaneCraftJob {
    private AspectList aspects = new AspectList();
    private ItemStack result = ItemStack.EMPTY;
    private ItemStack[] inputs = emptyGrid();
    private boolean produced;
    private final List<ItemStack> outputs = new ArrayList<>();

    public AspectList aspects() { return aspects; }
    public ItemStack result() { return result; }
    public ItemStack[] inputs() { return inputs; }
    public boolean produced() { return produced; }
    public List<ItemStack> outputs() { return outputs; }

    public void start(AspectList cost, ItemStack output, ItemStack[] actualInputs) {
        clear();
        aspects = cost.copy();
        result = output.copy();
        for (int i = 0; i < inputs.length && i < actualInputs.length; i++) {
            if (actualInputs[i] != null) inputs[i] = actualInputs[i].copy();
        }
    }

    public void produce(List<ItemStack> retainedInputs) {
        if (produced) throw new IllegalStateException("Arcane order was already produced");
        outputs.add(result.copy());
        for (var retained : retainedInputs) outputs.add(retained.copy());
        produced = true;
    }

    public void clearPreview() { inputs = emptyGrid(); }

    public void clear() {
        aspects = new AspectList();
        result = ItemStack.EMPTY;
        inputs = emptyGrid();
        produced = false;
        outputs.clear();
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        clear();
        result = ItemStack.parseOptional(registries, tag.getCompound("CurrentCraftResult"));
        aspects.readFromNBT(tag.getCompound("CurrentCraftAspects"));
        var grid = tag.getCompound("PreviewGrid");
        for (int i = 0; i < inputs.length; i++) inputs[i] = ItemStack.parseOptional(registries, grid.getCompound("g" + i));
        produced = tag.getBoolean("ResultProduced");
        for (var output : tag.getList("PendingCraftOutputs", Tag.TAG_COMPOUND)) {
            var stack = ItemStack.parseOptional(registries, (CompoundTag) output);
            if (!stack.isEmpty()) outputs.add(stack);
        }
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("ResultProduced", produced);
        tag.remove("CurrentCraftResult");
        if (!result.isEmpty()) tag.put("CurrentCraftResult", result.save(registries));
        var cost = new CompoundTag();
        aspects.writeToNBT(cost);
        tag.put("CurrentCraftAspects", cost);
        var grid = new CompoundTag();
        for (int i = 0; i < inputs.length; i++) if (!inputs[i].isEmpty()) grid.put("g" + i, inputs[i].save(registries));
        tag.put("PreviewGrid", grid);
        var pending = new ListTag();
        for (var output : outputs) if (!output.isEmpty()) pending.add(output.save(registries));
        tag.put("PendingCraftOutputs", pending);
    }

    private static ItemStack[] emptyGrid() {
        var grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        return grid;
    }
}
