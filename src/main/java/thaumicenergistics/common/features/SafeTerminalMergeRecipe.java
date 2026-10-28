package thaumicenergistics.common.features;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import thaumicenergistics.common.integration.ae2wt.SafeTerminalMerge;
import thaumicenergistics.common.integration.ae2wt.AE2WTCompat;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModRecipeSerializers;

public final class SafeTerminalMergeRecipe extends CustomRecipe {
    private final String partner;

    public SafeTerminalMergeRecipe(String partner) {
        super(CraftingBookCategory.EQUIPMENT);
        this.partner = partner;
    }

    private ItemStack merge(CraftingInput input) {
        if (!ModList.get().isLoaded("ae2wtlib") || input.ingredientCount() != 2) {
            return ItemStack.EMPTY;
        }
        ItemStack arcane = ItemStack.EMPTY;
        ItemStack other = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get())) {
                if (!arcane.isEmpty()) return ItemStack.EMPTY;
                arcane = stack;
            } else {
                other = stack;
            }
        }
        return arcane.isEmpty() || other.isEmpty() ? ItemStack.EMPTY
                : SafeTerminalMerge.merge(arcane, other, partner);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !merge(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return merge(input);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SAFE_TERMINAL_MERGE.get();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        var ingredients = NonNullList.<Ingredient>create();
        if (ModList.get().isLoaded("ae2wtlib")) {
            var other = AE2WTCompat.displayPartner(partner);
            if (!other.isEmpty()) {
                ingredients.add(Ingredient.of(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()));
                ingredients.add(Ingredient.of(other));
            }
        }
        return ingredients;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return displayResult();
    }

    public ItemStack displayResult() {
        if (!ModList.get().isLoaded("ae2wtlib")) return ItemStack.EMPTY;
        return SafeTerminalMerge.merge(new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                AE2WTCompat.displayPartner(partner), partner);
    }

    public static final class Serializer implements RecipeSerializer<SafeTerminalMergeRecipe> {
        private static final MapCodec<SafeTerminalMergeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(com.mojang.serialization.Codec.STRING.optionalFieldOf("partner", "")
                        .forGetter(recipe -> recipe.partner)).apply(instance, SafeTerminalMergeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, SafeTerminalMergeRecipe> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, recipe -> recipe.partner,
                        SafeTerminalMergeRecipe::new);

        @Override
        public MapCodec<SafeTerminalMergeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SafeTerminalMergeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
