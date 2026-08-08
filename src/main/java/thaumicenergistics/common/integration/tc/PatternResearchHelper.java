package thaumicenergistics.common.integration.tc;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.tiles.TileArcaneAssembler;

/**
 * 从 AE2 {@link IPatternDetails} 中提取 TC 研究键的辅助类。
 *
 * 两种样板携带研究键的方式：
 *  - 奥术装配器样板 {@link TileArcaneAssembler.ArcaneAssemblerPattern}：直接持 research 字段
 *  - 蒸馏处理样板（AE2 标准 processing pattern）：通过样板上 DataComponents.CUSTOM_DATA 的 research 字段标记
 */
public final class PatternResearchHelper {

    public static final String NBTKEY_RESEARCH = "research";

    private PatternResearchHelper() {}

    /**
     * 提取样板对应的 TC 研究键；无研究要求或未知样板时返回 null（表示不限制）。
     */
    @Nullable
    public static String getResearch(IPatternDetails pattern) {
        if (pattern instanceof TileArcaneAssembler.ArcaneAssemblerPattern arcanePattern) {
            return arcanePattern.getResearch();
        }

        // 蒸馏处理样板：从样板上 CUSTOM_DATA 读 research 标记
        AEItemKey definition = pattern.getDefinition();
        if (definition != null) {
            ItemStack patternStack = definition.toStack();
            CustomData customData = patternStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains(NBTKEY_RESEARCH)) {
                    String research = tag.getString(NBTKEY_RESEARCH);
                    return research.isEmpty() ? null : research;
                }
            }
        }
        return null;
    }
}
