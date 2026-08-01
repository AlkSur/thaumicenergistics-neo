package thaumicenergistics.common.integration.tc;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.storage.AspectStack;

public final class TCReflection {

    /** aspect tag → ResourceLocation 缓存（tag 有界：6 个源质 + 已注册方面）。 */
    private static final Map<String, ResourceLocation> ASPECT_ID_CACHE = new ConcurrentHashMap<>();

    private TCReflection() {}

    @Nullable
    public static AspectStack extractEssentia(@Nullable BlockEntity be, int maxAmount) {
        if (be == null) {
            ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: be is null");
            return null;
        }
        if (!(be instanceof IAspectContainer container)) {
            ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: {} does not implement IAspectContainer", be.getClass().getName());
            return null;
        }

        // ===== 路径A: 旧版 IAspectContainer API (getAspects) =====
        AspectList aspectList = container.getAspects();
        if (aspectList != null && aspectList.size() > 0) {
            Aspect[] aspects = aspectList.getAspects();
            if (aspects != null) {
                ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: found {} aspects via getAspects()", aspects.length);
                for (Aspect aspect : aspects) {
                    if (aspect == null) continue;
                    int amt = aspectList.getAmount(aspect);
                    if (amt <= 0) continue;
                    int toExtract = Math.min(amt, maxAmount);
                    if (container.takeFromContainer(aspect, toExtract)) {
                        be.setChanged();
                        ResourceLocation id = getAspectId(aspect);
                        ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: SUCCESS (API) - extracted {} of {}", toExtract, id);
                        return id != null ? new AspectStack(id, toExtract) : null;
                    }
                }
            }
        }

        // ===== 路径B: TC 移植版反射回退 (getAspect/getAmount) =====
        ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: getAspects() empty, trying reflection fallback for {}", be.getClass().getName());
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
                    ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: SUCCESS (reflection) - extracted {} of {}", toExtract, id);
                    return id != null ? new AspectStack(id, toExtract) : null;
                }
                ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: takeFromContainer returned false for {} of {}", toExtract, aspect.getTag());
            } else {
                ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: reflection got aspect={}, amount={}", aspect, amount);
            }
        } catch (Exception e) {
            ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: reflection fallback error: {}", e.getMessage());
        }

        ThaumicEnergistics.LOG.debug("[TCReflection] extractEssentia: no extractable aspect found");
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
        ThaumicEnergistics.LOG.debug("[TCReflection] insertEssentia: inserting {} of {} into {}", toInsert, stack.aspectId(), be.getClass().getName());
        int leftover = container.addToContainer(aspect, toInsert);
        int added = toInsert - leftover;
        ThaumicEnergistics.LOG.debug("[TCReflection] insertEssentia: added={}, leftover={}", added, leftover);
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