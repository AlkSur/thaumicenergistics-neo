package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import static mezz.jei.api.constants.RecipeTypes.CRAFTING;
import net.minecraft.resources.ResourceLocation;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.GuiEssentiaCellWorkbench;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * JEI 插件：将源质注册为自定义 IIngredient 类型显示在 JEI 成分列表中。
 * 拖拽功能由 AE2-JEI-Integration 模组的 GhostIngredientHandler + IngredientConverter 处理。
 * 不创建虚拟物品，不污染物品注册表。
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final Codec<AspectIngredient> CODEC = ResourceLocation.CODEC.flatXmap(
            rl -> {
                Aspect aspect = Aspect.getAspect(rl.getPath());
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
    public void registerIngredients(IModIngredientRegistration registration) {
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
        // 注册 IRecipeTransferInfo 让 JEI 显示 "+" 按钮
        registration.addRecipeTransferHandler(new ArcaneCraftingTransferInfo());
        registration.addRecipeTransferHandler(new ArcaneRecipeTransferInfo());
        // 注册 IRecipeTransferHandler 让点击 "+" 时走 ME 网络
        registration.addRecipeTransferHandler(new ThEUseCraftingRecipeTransfer(registration.getTransferHelper()), CRAFTING);
        registration.addRecipeTransferHandler(new ThEUseArcaneRecipeTransfer(registration.getTransferHelper()), ThaumcraftJeiPlugin.ARCANE);

        // 知识记录仪：奥术配方 "+" 一键放置到 3×3 ghost 合成网格
        registration.addRecipeTransferHandler(new KnowledgeInscriberTransferInfo());
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberArcaneRecipeTransfer(registration.getTransferHelper()), ThaumcraftJeiPlugin.ARCANE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 源质元件工作台：从 JEI 拖拽要素到分区格添加分区（纯 JEI API，可选功能）
        registration.addGhostIngredientHandler(GuiEssentiaCellWorkbench.class, new CellWorkbenchGhostIngredientHandler());
        // 知识记录仪：从 JEI 拉取物品到 3×3 合成网格（复用 GhostSlot + KnowledgeInscriberGhostSlotPacket）
        registration.addGhostIngredientHandler(
                thaumicenergistics.common.container.GuiKnowledgeInscriber.class,
                new KnowledgeInscriberGhostIngredientHandler());
        // 蒸馏编码台：从 JEI 拉取物品到源物品槽（C2S 设置源物品）
        registration.addGhostIngredientHandler(
                thaumicenergistics.common.container.GuiDistillationPatternEncoder.class,
                new DistillationEncoderGhostIngredientHandler());
    }
}
