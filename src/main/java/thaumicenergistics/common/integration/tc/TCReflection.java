package thaumicenergistics.common.integration.tc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.common.essentia.EssentiaTransport;
import thaumicenergistics.api.storage.AspectStack;

public final class TCReflection {

    /** 荒古炼金炉类名（TC port；不实现 IAspectContainer，需反射抽内部源质）。 */
    private static final String FURNACE_CLASS_NAME = "thaumcraft.common.blockentities.AlchemicalFurnaceBlockEntity";

    private TCReflection() {}

    @Nullable
    public static AspectStack extractEssentia(@Nullable BlockEntity be, int maxAmount) {
        return extractEssentia(be, null, maxAmount);
    }

    /**
     * 从容器抽取源质。only 非空时只抽指定方面（存储总线按 AE 请求的 key 定向抽取）。
     */
    @Nullable
    public static AspectStack extractEssentia(@Nullable BlockEntity be, @Nullable ResourceLocation only, int maxAmount) {
        if (be == null) {
            return null;
        }
        // ===== 路径0: 荒古炼金炉直连（AlchemicalFurnaceBlockEntity 不实现 IAspectContainer，
        // 源质在 private aspects 缓存——直接抽取，替代"必须经蒸馏釜/罐子"）=====
        if (FURNACE_CLASS_NAME.equals(be.getClass().getName())) {
            AspectStack fromFurnace = LegacyFurnaceEssentia.extractFromFurnace(be, only, maxAmount);
            if (fromFurnace != null) return fromFurnace;
        }
        // ===== 路径0b: TC 管道接口（EssentiaTransport——高级荒古炼金炉喷嘴/源质管道）=====
        if (be instanceof EssentiaTransport transport) {
            for (Direction face : Direction.values()) {
                if (!transport.canOutputTo(face)) continue;
                Aspect aspect = transport.getEssentiaType(face);
                if (aspect == null) continue;
                ResourceLocation id = getAspectId(aspect);
                if (id == null) continue;
                if (only != null && !only.equals(id)) continue;
                int amount = transport.getEssentiaAmount(face);
                if (amount <= 0) continue;
                int toExtract = Math.min(amount, maxAmount);
                int taken = transport.takeEssentia(aspect, toExtract, face);
                if (taken > 0) {
                    be.setChanged();
                    return new AspectStack(id, taken);
                }
            }
        }
        if (!(be instanceof IAspectContainer container)) {
            return null;
        }

        // ===== 路径A: 旧版 IAspectContainer API (getAspects) =====
        AspectList aspectList = container.getAspects();
        if (aspectList != null && aspectList.size() > 0) {
            Aspect[] aspects = aspectList.getAspects();
            if (aspects != null) {
                for (Aspect aspect : aspects) {
                    if (aspect == null) continue;
                    int amt = aspectList.getAmount(aspect);
                    if (amt <= 0) continue;
                    ResourceLocation id = getAspectId(aspect);
                    if (id == null) continue;
                    if (only != null && !only.equals(id)) continue;
                    int toExtract = Math.min(amt, maxAmount);
                    if (container.takeFromContainer(aspect, toExtract)) {
                        be.setChanged();
                        return new AspectStack(id, toExtract);
                    }
                }
            }
        }

        // ===== 路径B: TC 移植版反射回退 (getAspect/getAmount) =====
        try {
            java.lang.reflect.Method[] methods = getAspectMethods(be.getClass());
            if (methods.length < 2) {
                return null;
            }
            Aspect aspect = (Aspect) methods[0].invoke(be);
            int amount = (int) methods[1].invoke(be);
            if (aspect != null && amount > 0) {
                ResourceLocation id = getAspectId(aspect);
                if (id == null || (only != null && !only.equals(id))) {
                    return null;
                }
                int toExtract = Math.min(amount, maxAmount);
                if (container.takeFromContainer(aspect, toExtract)) {
                    be.setChanged();
                    return new AspectStack(id, toExtract);
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public static void returnEssentia(@Nullable BlockEntity be, AspectStack stack) {
        if (be == null || stack.isEmpty()) return;
        Aspect aspect = findAspectById(stack.aspectId());
        if (aspect == null) return;
        // 荒古炼金炉：不实现 IAspectContainer，源质在 private aspects 字段 → 反射写回。
        // （此前直接 return 导致导入总线"网络已满"时抽出的源质无处归还而消失）
        if (FURNACE_CLASS_NAME.equals(be.getClass().getName())) {
            LegacyFurnaceEssentia.addToFurnace(be, aspect, (int) stack.amount());
            return;
        }
        if (!(be instanceof IAspectContainer container)) return;
        container.addToContainer(aspect, (int) stack.amount());
        be.setChanged();
    }

    public static int insertEssentia(@Nullable BlockEntity be, AspectStack stack) {
        if (be == null || stack.isEmpty()) return 0;
        Aspect aspect = findAspectById(stack.aspectId());
        if (aspect == null) return 0;
        int toInsert = (int) Math.min(stack.amount(), Integer.MAX_VALUE);
        if (FURNACE_CLASS_NAME.equals(be.getClass().getName())) {
            return LegacyFurnaceEssentia.addToFurnace(be, aspect, toInsert);
        }
        if (!(be instanceof IAspectContainer container)) return 0;
        int leftover = container.addToContainer(aspect, toInsert);
        int added = toInsert - leftover;
        if (added > 0) {
            be.setChanged();
        }
        return added;
    }

    /** 非破坏性列出容器内全部源质（存储总线挂载为网络存储、导入总线预检用）。 */
    public static java.util.List<AspectStack> listEssentia(@Nullable BlockEntity be) {
        java.util.List<AspectStack> result = new java.util.ArrayList<>();
        if (be == null) return result;
        // 荒古炼金炉（不实现 IAspectContainer）
        if (FURNACE_CLASS_NAME.equals(be.getClass().getName())) {
            AspectList furnaceAspects = LegacyFurnaceEssentia.getFurnaceAspects(be);
            if (furnaceAspects != null) {
                for (Aspect aspect : furnaceAspects.getAspects()) {
                    if (aspect == null) continue;
                    int amt = furnaceAspects.getAmount(aspect);
                    ResourceLocation id = getAspectId(aspect);
                    if (amt > 0 && id != null) result.add(new AspectStack(id, amt));
                }
            }
            return result;
        }
        // TC 管道接口
        if (be instanceof EssentiaTransport transport) {
            for (Direction face : Direction.values()) {
                if (!transport.canOutputTo(face)) continue;
                Aspect aspect = transport.getEssentiaType(face);
                if (aspect == null) continue;
                int amount = transport.getEssentiaAmount(face);
                ResourceLocation id = getAspectId(aspect);
                if (amount > 0 && id != null) result.add(new AspectStack(id, amount));
            }
            return result;
        }
        // IAspectContainer（罐子/奥术蒸馏器等）
        if (be instanceof IAspectContainer container) {
            AspectList list = container.getAspects();
            if (list != null) {
                for (Aspect aspect : list.getAspects()) {
                    if (aspect == null) continue;
                    int amt = list.getAmount(aspect);
                    ResourceLocation id = getAspectId(aspect);
                    if (amt > 0 && id != null) result.add(new AspectStack(id, amt));
                }
            }
        }
        return result;
    }

    /** 非破坏性窥视第一个可抽取的源质（导入总线预检：先看容器能给什么，再决定抽多少）。 */
    @Nullable
    public static AspectStack peekEssentia(@Nullable BlockEntity be, int maxAmount) {
        for (AspectStack stack : listEssentia(be)) {
            if (stack.isEmpty()) continue;
            long amt = Math.min(stack.amount(), Math.max(0, maxAmount));
            if (amt > 0) return new AspectStack(stack.aspectId(), amt);
        }
        return null;
    }

    @Nullable
    public static Aspect findAspectById(ResourceLocation id) {
        return AspectIds.findAspectById(id);
    }

    /** 按方块实体类缓存反射方法（getAspect/getAmount），避免每次调用 getMethod。 */
    private static final Map<Class<?>, java.lang.reflect.Method[]> REFLECT_METHOD_CACHE = new ConcurrentHashMap<>();

    private static java.lang.reflect.Method[] getAspectMethods(Class<?> clazz) {
        return REFLECT_METHOD_CACHE.computeIfAbsent(clazz, c -> {
            try {
                return new java.lang.reflect.Method[] { c.getMethod("getAspect"), c.getMethod("getAmount") };
            } catch (NoSuchMethodException e) {
                return new java.lang.reflect.Method[0];
            }
        });
    }

    @Nullable
    public static ResourceLocation getAspectId(@Nullable Aspect aspect) {
        return AspectIds.getAspectId(aspect);
    }

}