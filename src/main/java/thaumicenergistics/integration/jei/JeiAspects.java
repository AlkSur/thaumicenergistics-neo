package thaumicenergistics.integration.jei;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import mezz.jei.api.ingredients.IIngredientType;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;

/**
 * One aspect ingredient in JEI (TEL-01). SCEX's Thaumcraft Aspects JEI ({@code scex_aspect_jei}) registers its own
 * aspect ingredient with combination and source pages; when it is installed TE does not register a second list
 * and its drag-and-drop targets, converters and EAP hook accept that type as well as TE's own.
 */
public final class JeiAspects {
    public static final String SHARED_MOD = "scex_aspect_jei";
    private static final String SHARED_CLASS = "dev.scex.aspectjei.AspectIngredient";

    private static volatile boolean resolved;
    @Nullable private static Class<?> sharedClass;
    @Nullable private static Method sharedAspect;
    @Nullable private static Constructor<?> sharedConstructor;
    @Nullable private static IIngredientType<?> sharedType;

    private JeiAspects() {
    }

    /** Whether the shared aspect ingredient is in use (the other mod is installed and its type was found). */
    public static boolean useShared() {
        resolve();
        return sharedType != null;
    }

    @Nullable
    public static IIngredientType<?> sharedType() {
        resolve();
        return sharedType;
    }

    /** The aspect of a JEI ingredient of either type, or null. */
    @Nullable
    public static Aspect aspectOf(@Nullable Object ingredient) {
        if (ingredient instanceof AspectIngredient own) return own.aspect();
        resolve();
        if (ingredient != null && sharedClass != null && sharedClass.isInstance(ingredient) && sharedAspect != null) {
            try {
                return (Aspect) sharedAspect.invoke(ingredient);
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                return null;
            }
        }
        return null;
    }

    /** An ingredient of the type JEI lists for {@code aspect}. */
    public static Object ingredientFor(Aspect aspect) {
        resolve();
        if (sharedConstructor != null) {
            try {
                return sharedConstructor.newInstance(aspect);
            } catch (ReflectiveOperationException ignored) {
                // fall through to TE's own type
            }
        }
        return new AspectIngredient(aspect);
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (JeiAspects.class) {
            if (resolved) return;
            try {
                if (ModList.get() != null && ModList.get().isLoaded(SHARED_MOD)) {
                    Class<?> type = Class.forName(SHARED_CLASS);
                    Method aspect = type.getMethod("aspect");
                    Constructor<?> constructor = type.getConstructor(Aspect.class);
                    Object field = type.getField("TYPE").get(null);
                    if (field instanceof IIngredientType<?> ingredientType) {
                        sharedClass = type;
                        sharedAspect = aspect;
                        sharedConstructor = constructor;
                        sharedType = ingredientType;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                thaumicenergistics.ThaumicEnergistics.LOG.warn("[JEI] {} present but its aspect type is unusable: {}",
                        SHARED_MOD, e.toString());
            }
            resolved = true;
        }
    }
}
