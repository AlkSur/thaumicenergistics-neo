package tc_energy.integration.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.recipe.RecipeType;

import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.research.ResearchKey;

/**
 * Ren 版 TC JEI 桥（反射）：dev.tc4port.thaumcraft.compat.jei.ThaumcraftJeiPlugin.ARCANE
 * 与 ArcaneJeiRecipe 是包私有，ThE 无法编译期直连，运行期反射读取。
 * <p>
 * RecipeType 按 uid（thaumcraft:arcane）等价匹配 JEI 注册的 ARCANE 分类；
 * ArcaneJeiRecipe record 分量通过同名访问器读取。
 */
public final class ThEArcaneJeiBridge {

    /** JEI RecipeType（uid = thaumcraft:arcane），泛型擦除后按 Object 处理。 */
    public static final RecipeType<Object> ARCANE;

    private static final Method M_INGREDIENTS;
    private static final Method M_WIDTH;
    private static final Method M_HEIGHT;
    private static final Method M_OUTPUT;
    private static final Method M_VIS;
    private static final Method M_RESEARCH;

    static {
        try {
            Class<?> pluginClass = Class.forName("dev.tc4port.thaumcraft.compat.jei.ThaumcraftJeiPlugin");
            Field f = pluginClass.getDeclaredField("ARCANE");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            RecipeType<Object> type = (RecipeType<Object>) f.get(null);
            ARCANE = type;

            Class<?> recipeClass = Class.forName("dev.tc4port.thaumcraft.compat.jei.ArcaneJeiRecipe");
            // ⚠ ArcaneJeiRecipe 是包私有 record（dev.tc4port.thaumcraft.compat.jei）。
            // 记录访问器本身是 public，但"声明类"包私有 → 跨包 Method.invoke 会抛
            // IllegalAccessException（被各方法吞掉后返回空列表 → JEI "+" 转移全变成空气）。
            // 必须对每个 Method 调 setAccessible(true)。
            M_INGREDIENTS = accessible(recipeClass.getMethod("ingredients"));
            M_WIDTH = accessible(recipeClass.getMethod("width"));
            M_HEIGHT = accessible(recipeClass.getMethod("height"));
            M_OUTPUT = accessible(recipeClass.getMethod("output"));
            M_VIS = accessible(recipeClass.getMethod("vis"));
            M_RESEARCH = accessible(recipeClass.getMethod("research"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ThE: Ren TC JEI bridge init failed", e);
        }
    }

    private ThEArcaneJeiBridge() {}

    /** 打开包私有声明类上的方法访问权限，避免跨包 invoke 抛 IllegalAccessException。 */
    private static Method accessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
            // 模块受限时保持原样（失败时 invoke 会显式抛 IllegalAccessException）
        }
        return method;
    }

    public static List<Ingredient> ingredients(Object recipe) {
        try {
            return (List<Ingredient>) M_INGREDIENTS.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    public static int width(Object recipe) {
        try {
            return (Integer) M_WIDTH.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public static int height(Object recipe) {
        try {
            return (Integer) M_HEIGHT.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public static ItemStack output(Object recipe) {
        try {
            return (ItemStack) M_OUTPUT.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return ItemStack.EMPTY;
        }
    }

    public static java.util.Map<VisChannel, Integer> vis(Object recipe) {
        try {
            return (java.util.Map<VisChannel, Integer>) M_VIS.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return java.util.Map.of();
        }
    }

    public static java.util.List<ResearchKey> research(Object recipe) {
        try {
            return (java.util.List<ResearchKey>) M_RESEARCH.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return java.util.List.of();
        }
    }

    /** 研究键序列化形式（供存样板/校验）。 */
    public static java.util.List<String> researchSerialized(Object recipe) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (ResearchKey key : research(recipe)) {
            out.add(key.serialized());
        }
        return out;
    }

    /** 配方 id。 */
    public static ResourceLocation id(Object recipe) {
        try {
            Method m = recipe.getClass().getMethod("id");
            m.setAccessible(true);
            return (ResourceLocation) m.invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
