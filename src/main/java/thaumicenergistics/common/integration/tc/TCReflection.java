package thaumicenergistics.common.integration.tc;

import java.util.Locale;
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

    /** aspect tag → ResourceLocation 缓存（tag 有界：6 个源质 + 已注册方面）。 */
    private static final Map<String, ResourceLocation> ASPECT_ID_CACHE = new ConcurrentHashMap<>();

    /** 荒古炼金炉类名（TC port；不实现 IAspectContainer，需反射抽内部源质）。 */
    private static final String FURNACE_CLASS_NAME = "thaumcraft.common.blockentities.AlchemicalFurnaceBlockEntity";
    /** 炼金炉 private aspects 字段（缓存 Field 避免每 tick getDeclaredField）。 */
    private static volatile java.lang.reflect.Field furnaceAspectsField;

    private TCReflection() {}

    /** 从荒古炼金炉内部源质缓存直接抽取（替代蒸馏釜/罐子中转）。 */
    @Nullable
    private static AspectStack extractFromFurnace(BlockEntity be, int maxAmount) {
        try {
            if (furnaceAspectsField == null) {
                furnaceAspectsField = be.getClass().getDeclaredField("aspects");
                furnaceAspectsField.setAccessible(true);
            }
            AspectList furnaceAspects = (AspectList) furnaceAspectsField.get(be);
            if (furnaceAspects != null && furnaceAspects.size() > 0) {
                Aspect[] aspects = furnaceAspects.getAspects();
                for (Aspect aspect : aspects) {
                    if (aspect == null) continue;
                    int amt = furnaceAspects.getAmount(aspect);
                    if (amt <= 0) continue;
                    int toExtract = Math.min(amt, maxAmount);
                    furnaceAspects.remove(aspect, toExtract);
                    be.setChanged();
                    ResourceLocation id = getAspectId(aspect);
                    return id != null ? new AspectStack(id, toExtract) : null;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public static AspectStack extractEssentia(@Nullable BlockEntity be, int maxAmount) {
        if (be == null) {
            return null;
        }
        // ===== 路径0: 荒古炼金炉直连（AlchemicalFurnaceBlockEntity 不实现 IAspectContainer，
        // 源质在 private aspects 缓存——直接抽取，替代"必须经蒸馏釜/罐子"）=====
        if (FURNACE_CLASS_NAME.equals(be.getClass().getName())) {
            AspectStack fromFurnace = extractFromFurnace(be, maxAmount);
            if (fromFurnace != null) return fromFurnace;
        }
        // ===== 路径0b: TC 管道接口（EssentiaTransport——高级荒古炼金炉喷嘴/源质管道）=====
        if (be instanceof EssentiaTransport transport) {
            for (Direction face : Direction.values()) {
                if (!transport.canOutputTo(face)) continue;
                Aspect aspect = transport.getEssentiaType(face);
                if (aspect == null) continue;
                int amount = transport.getEssentiaAmount(face);
                if (amount <= 0) continue;
                int toExtract = Math.min(amount, maxAmount);
                int taken = transport.takeEssentia(aspect, toExtract, face);
                if (taken > 0) {
                    be.setChanged();
                    ResourceLocation id = getAspectId(aspect);
                    return id != null ? new AspectStack(id, taken) : null;
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
                    int toExtract = Math.min(amt, maxAmount);
                    if (container.takeFromContainer(aspect, toExtract)) {
                        be.setChanged();
                        ResourceLocation id = getAspectId(aspect);
                        return id != null ? new AspectStack(id, toExtract) : null;
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
                int toExtract = Math.min(amount, maxAmount);
                if (container.takeFromContainer(aspect, toExtract)) {
                    be.setChanged();
                    ResourceLocation id = getAspectId(aspect);
                    return id != null ? new AspectStack(id, toExtract) : null;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public static void returnEssentia(@Nullable BlockEntity be, AspectStack stack) {
        if (be == null || stack.isEmpty()) return;
        if (!(be instanceof IAspectContainer container)) return;
        Aspect aspect = findAspectById(stack.aspectId());
        if (aspect == null) return;
        container.addToContainer(aspect, (int) stack.amount());
        be.setChanged();
    }

    public static int insertEssentia(@Nullable BlockEntity be, AspectStack stack) {
        if (be == null || stack.isEmpty()) return 0;
        if (!(be instanceof IAspectContainer container)) return 0;
        Aspect aspect = findAspectById(stack.aspectId());
        if (aspect == null) return 0;
        int toInsert = (int) stack.amount();
        int leftover = container.addToContainer(aspect, toInsert);
        int added = toInsert - leftover;
        if (added > 0) {
            be.setChanged();
        }
        return added;
    }

    @Nullable
    public static Aspect findAspectById(ResourceLocation id) {
        if (id == null) return null;
        if (Aspect.aspects == null) return null;
        ensureAspectCache();
        String lowerTag = id.getPath().toLowerCase(Locale.ROOT);
        Aspect cached = ASPECT_BY_TAG.get(lowerTag);
        if (cached != null) return cached;
        // 缓存 miss：运行期（post-init）可能注册了新 aspect，回退一次实时遍历并补入缓存
        for (Aspect aspect : Aspect.aspects.values()) {
            if (aspect != null && aspect.getTag() != null && aspect.getTag().equalsIgnoreCase(id.getPath())) {
                ASPECT_BY_TAG.putIfAbsent(lowerTag, aspect);
                return aspect;
            }
        }
        return null;
    }

    /** tag(小写) → Aspect 反向缓存，避免每次调用 O(n) 遍历 Aspect.aspects。 */
    private static final Map<String, Aspect> ASPECT_BY_TAG = new ConcurrentHashMap<>();
    private static volatile boolean aspectCacheBuilt = false;

    private static void ensureAspectCache() {
        if (aspectCacheBuilt) return;
        synchronized (ASPECT_BY_TAG) {
            if (aspectCacheBuilt) return;
            for (Aspect aspect : Aspect.aspects.values()) {
                if (aspect != null && aspect.getTag() != null) {
                    ASPECT_BY_TAG.putIfAbsent(aspect.getTag().toLowerCase(Locale.ROOT), aspect);
                }
            }
            aspectCacheBuilt = true;
        }
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
        if (aspect == null) return null;
        String tag = aspect.getTag();
        if (tag == null || tag.isEmpty()) return null;
        return ASPECT_ID_CACHE.computeIfAbsent(tag.toLowerCase(Locale.ROOT), t ->
                ResourceLocation.fromNamespaceAndPath("thaumcraft", t));
    }
}