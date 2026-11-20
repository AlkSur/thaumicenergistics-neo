package thaumicenergistics.integration.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.GuiDistillationPatternEncoder;
import thaumicenergistics.common.container.GuiEssentiaCellWorkbench;
import thaumicenergistics.common.container.GuiKnowledgeInscriber;
import thaumicenergistics.common.features.SafeTerminalMergeRecipe;
import thaumicenergistics.common.integration.tc.TCReflection;

import static mezz.jei.api.constants.RecipeTypes.CRAFTING;

/**
 * JEI 插件：将源质注册为自定义 IIngredient 类型显示在 JEI 成分列表中。
 * 拖拽功能由 AE2-JEI-Integration 模组的 GhostIngredientHandler + IngredientConverter 处理。
 * 不创建虚拟物品，不污染物品注册表。
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final Codec<AspectIngredient> CODEC = ResourceLocation.CODEC.flatXmap(
            rl -> {
                Aspect aspect = thaumicenergistics.common.integration.tc.AspectIds.findAspectById(rl);
                if (aspect != null) return DataResult.success(new AspectIngredient(aspect));
                return DataResult.error(() -> "Unknown aspect: " + rl);
            },
            ai -> {
                ResourceLocation id = TCReflection.getAspectId(ai.aspect());
                ResourceLocation result = id != null ? id : ResourceLocation.withDefaultNamespace(ai.aspect().getTag());
                return DataResult.success(result);
            }
    );

    public ThEJeiPlugin() {
        // 可选集成：AE2-JEI-Integration 存在时注册源质拖拽转换（拖拽进 AE2 筛选槽）；
        // 不存在时跳过——只装 JEI 也能正常使用 JEI 显示/配方转移/ghost 拖拽。
        try {
            Class.forName("tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters");
            Class.forName("thaumicenergistics.integration.jei.Ae2JeiCompat")
                    .getMethod("register").invoke(null);
        } catch (ClassNotFoundException e) {
            // AE2-JEI-Integration 未安装：跳过源质拖拽转换（可选功能，不影响其他 JEI 功能）
        } catch (ReflectiveOperationException e) {
            ThaumicEnergistics.LOG.warn("[JEI] AE2-JEI-Integration registration failed: {}", e.toString());
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ThaumicEnergistics.id("jei");
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(SafeTerminalMergeRecipe.class,
                new ICraftingCategoryExtension<SafeTerminalMergeRecipe>() {
                    @Override
                    public void setRecipe(RecipeHolder<SafeTerminalMergeRecipe> holder, IRecipeLayoutBuilder builder,
                            ICraftingGridHelper grid, IFocusGroup focuses) {
                        grid.createAndSetIngredients(builder, holder.value().getIngredients(), 0, 0);
                        grid.createAndSetOutputs(builder, List.of(holder.value().displayResult()));
                        builder.setShapeless();
                    }
                });
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        // scex_aspect_jei already lists every aspect (with combination and source pages): one list, not two.
        if (JeiAspects.useShared()) {
            return;
        }
        List<AspectIngredient> ingredients = new ArrayList<>();
        if (Aspect.aspects != null) {
            for (Aspect aspect : Aspect.aspects.values()) {
                if (aspect != null && aspect.getTag() != null) {
                    ingredients.add(new AspectIngredient(aspect));
                }
            }
        }
        registration.register(
                AspectIngredientType.INSTANCE,
                ingredients,
                AspectIngredientHelper.INSTANCE,
                AspectIngredientRenderer.INSTANCE,
                CODEC
        );
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        InfusionPatternTransfer.register(registration);
        // One network-aware handler per container class/category covers both menu types.
        // Separate basic slot mappings would overwrite each other in JEI's registration table.
        registration.addRecipeTransferHandler(new ThEUseCraftingRecipeTransfer(registration.getTransferHelper()), CRAFTING);
        registration.addRecipeTransferHandler(new ThEUseArcaneRecipeTransfer(registration.getTransferHelper()), ThaumcraftJeiPlugin.ARCANE);

        // 知识记录仪：奥术配方 "+" 一键放置到 3×3 ghost 合成网格
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberArcaneRecipeTransfer(registration.getTransferHelper()), ThaumcraftJeiPlugin.ARCANE);
    }

    /** Machines that do arcane crafting show up under "uses" of the Arcane category (TEU-08). */
    @Override
    public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration) {
        for (var item : List.of(thaumicenergistics.init.ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get(),
                thaumicenergistics.init.ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                thaumicenergistics.init.ModItems.ARCANE_ASSEMBLER_ITEM.get(),
                thaumicenergistics.init.ModBlocks.KNOWLEDGE_INSCRIBER.get().asItem())) {
            registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(item), ThaumcraftJeiPlugin.ARCANE);
        }
    }

    /** Information pages for things JEI cannot show as a recipe (TEU-08). */
    @Override
    public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration) {
        info(registration, thaumicenergistics.init.ModItems.ESSENTIA_UPGRADE_CARD.get(), "essentia_card");
        info(registration, thaumicenergistics.init.ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get().asItem(), "vibration_chamber");
        info(registration, thaumicenergistics.init.ModItems.KNOWLEDGE_CORE.get(), "knowledge_core");
        info(registration, thaumicenergistics.init.ModItems.VIS_INTERFACE.get(), "vis_interface");
        info(registration, thaumicenergistics.init.ModItems.PROBABILITY_ACCELERATOR_CARD.get(), "accelerator_card");
        for (var block : List.of(thaumicenergistics.init.ModBlocks.FLAWLESS_BUDDING_THAUMIC_CRYSTAL,
                thaumicenergistics.init.ModBlocks.FLAWED_BUDDING_THAUMIC_CRYSTAL,
                thaumicenergistics.init.ModBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL,
                thaumicenergistics.init.ModBlocks.DAMAGED_BUDDING_THAUMIC_CRYSTAL)) {
            info(registration, block.get().asItem(), "budding_crystal");
        }
    }

    private static void info(mezz.jei.api.registration.IRecipeRegistration registration,
            net.minecraft.world.item.Item item, String key) {
        registration.addIngredientInfo(new net.minecraft.world.item.ItemStack(item),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                net.minecraft.network.chat.Component.translatable("thaumicenergistics.jei.info." + key));
    }

    /** The upper half of the Brain of Probability is a placement helper, not an item to look up (TEU-15). */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                List.of(new net.minecraft.world.item.ItemStack(thaumicenergistics.init.ModBlocks.BRAIN_OF_PROBABILITY_TOP.get())));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 源质元件工作台：从 JEI 拖拽要素到分区格添加分区（纯 JEI API，可选功能）
        registration.addGhostIngredientHandler(GuiEssentiaCellWorkbench.class, new CellWorkbenchGhostIngredientHandler());
        // 知识记录仪：从 JEI 拉取物品到 3×3 合成网格（复用 GhostSlot + KnowledgeInscriberGhostSlotPacket）
        registration.addGhostIngredientHandler(
                GuiKnowledgeInscriber.class,
                new KnowledgeInscriberGhostIngredientHandler());
        // 蒸馏编码台：从 JEI 拉取物品到源物品槽（C2S 设置源物品）
        registration.addGhostIngredientHandler(
                GuiDistillationPatternEncoder.class,
                new DistillationEncoderGhostIngredientHandler());
    }
}
