package thaumicenergistics.common.integration.tc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumicenergistics.api.storage.AspectStack;

/** port.261 炼金炉的私有方面缓存兼容，公开 capability 无法覆盖时才调用。 */
final class LegacyFurnaceEssentia {
    private static volatile java.lang.reflect.Field furnaceAspectsField;
    private static final java.util.Set<Class<?>> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private LegacyFurnaceEssentia() { }

    /** 读取荒古炼金炉 private aspects 字段（不实现 IAspectContainer；只读，返回活引用用于原地修改）。 */
    @Nullable
    static AspectList getFurnaceAspects(BlockEntity be) {
        try {
            if (furnaceAspectsField == null) {
                furnaceAspectsField = be.getClass().getDeclaredField("aspects");
                furnaceAspectsField.setAccessible(true);
            }
            Object raw = furnaceAspectsField.get(be);
            return raw instanceof AspectList list ? list : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (WARNED.add(be.getClass())) thaumicenergistics.ThaumicEnergistics.LOG.warn(
                    "Legacy furnace essentia adapter cannot read {}: {}", be.getClass().getName(), e.toString());
            return null;
        }
    }

    /** 荒古炼金炉写回（aspects 为 private 字段，无公开写入 API）。返回实际写入量。 */
    static int addToFurnace(BlockEntity be, Aspect aspect, int amount) {
        if (amount <= 0) return 0;
        try {
            AspectList list = getFurnaceAspects(be);
            if (list == null) {
                if (furnaceAspectsField == null) return 0;
                list = new AspectList();
                furnaceAspectsField.set(be, list);
            }
            list.add(aspect, amount);
            be.setChanged();
            return amount;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 从荒古炼金炉内部源质缓存直接抽取（替代蒸馏釜/罐子中转）。only 非空时只抽指定方面。 */
    @Nullable
    static AspectStack extractFromFurnace(BlockEntity be, @Nullable ResourceLocation only, int maxAmount) {
        AspectList furnaceAspects = getFurnaceAspects(be);
        if (furnaceAspects == null || furnaceAspects.size() <= 0) return null;
        for (Aspect aspect : furnaceAspects.getAspects()) {
            if (aspect == null) continue;
            int amt = furnaceAspects.getAmount(aspect);
            if (amt <= 0) continue;
            ResourceLocation id = AspectIds.getAspectId(aspect);
            if (id == null) continue;
            if (only != null && !only.equals(id)) continue;
            int toExtract = Math.min(amt, maxAmount);
            furnaceAspects.remove(aspect, toExtract);
            be.setChanged();
            return new AspectStack(id, toExtract);
        }
        return null;
    }

}
