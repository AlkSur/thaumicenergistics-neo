package thaumicenergistics.common.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.integration.tc.ArcaneCraftingPattern;
import thaumicenergistics.common.tiles.TileArcaneAssembler;
import thaumicenergistics.init.ModItems;

/** CPU 持久化使用完整样板定义，不能用产物物品代替不同配方的身份。 */
public final class ArcanePatternDecoder implements IPatternDetailsDecoder {
    private static final String PATTERN = "ThEEncodedArcanePattern";

    public static AEItemKey definition(ArcaneCraftingPattern pattern) {
        var stack = new ItemStack(ModItems.KNOWLEDGE_CORE.get());
        var data = new CompoundTag();
        data.put(PATTERN, pattern.writeToNBT(new CompoundTag()));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return AEItemKey.of(stack);
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(ModItems.KNOWLEDGE_CORE.get()) && data != null
                && data.copyTag().contains(PATTERN, Tag.TAG_COMPOUND);
    }

    @Override
    @Nullable
    public IPatternDetails decodePattern(AEItemKey key, Level level) {
        // 旧存档样板内引用已删除物品时，上游（如 AE2LT rebuild）可能传入 null key，
        // 抛异常会导致整个供应器 BE 被 Skipping 丢数据，此处必须返回 null 而非抛出。
        if (key == null) return null;
        var stack = key.toStack();
        if (stack.isEmpty() || !isEncodedPattern(stack)) return null;
        try {
            var data = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            var pattern = new ArcaneCraftingPattern(data.getCompound(PATTERN));
            return pattern.isPatternValid() ? TileArcaneAssembler.buildArcanePattern(pattern) : null;
        } catch (Exception e) {
            // 损坏样板隔离：解析失败只禁用该样板配方，不清槽位、不改原始 NBT。
            return null;
        }
    }
}
