package thaumicenergistics.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.features.SafeTerminalMergeRecipe;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ThaumicEnergistics.MODID);
    public static final Supplier<RecipeSerializer<SafeTerminalMergeRecipe>> SAFE_TERMINAL_MERGE =
            REGISTRY.register("safe_terminal_merge", SafeTerminalMergeRecipe.Serializer::new);

    private ModRecipeSerializers() {
    }
}
