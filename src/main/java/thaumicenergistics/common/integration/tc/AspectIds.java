package thaumicenergistics.common.integration.tc;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;

/** 不可变要素键转换与注册表查找，不包含方块反射或资源转移。 */
public final class AspectIds {
    private static final Map<String, ResourceLocation> ASPECT_ID_CACHE = new ConcurrentHashMap<>();
    private AspectIds() { }

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

    @Nullable
    public static ResourceLocation getAspectId(@Nullable Aspect aspect) {
        if (aspect == null) return null;
        String tag = aspect.getTag();
        if (tag == null || tag.isEmpty()) return null;
        return ASPECT_ID_CACHE.computeIfAbsent(tag.toLowerCase(Locale.ROOT), t ->
                ResourceLocation.fromNamespaceAndPath("thaumcraft", t));
    }
}
