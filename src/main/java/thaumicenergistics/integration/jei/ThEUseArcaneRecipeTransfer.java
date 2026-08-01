package thaumicenergistics.integration.jei;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import org.jetbrains.annotations.Nullable;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import net.neoforged.neoforge.network.PacketDistributor;

import thaumcraft.integration.jei.JeiArcaneRecipe;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 奥术合成终端专用的 JEI 奥术配方转移处理器。
 * 点击 "+" 时发送 FillCraftingGridFromRecipePacket 从 ME 网络填充材料。
 */
public class ThEUseArcaneRecipeTransfer
        implements IRecipeTransferHandler<ContainerArcaneCraftingTerminal, JeiArcaneRecipe> {

    private final IRecipeTransferHandlerHelper helper;

    public ThEUseArcaneRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends ContainerArcaneCraftingTerminal> getContainerClass() {
        return ContainerArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<ContainerArcaneCraftingTerminal>> getMenuType() {
        return Optional.of(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get());
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<JeiArcaneRecipe> getRecipeType() {
        return ThaumcraftJeiPlugin.ARCANE;
    }

    @Override
    public IRecipeTransferError transferRecipe(
            ContainerArcaneCraftingTerminal menu,
            JeiArcaneRecipe recipe,
            IRecipeSlotsView display,
            net.minecraft.world.entity.player.Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        ThaumicEnergistics.LOG.info("[JEI-Arcane] transferRecipe called, recipeId={}, doTransfer={}",
                recipe.id(), doTransfer);

        boolean craftMissing = AbstractContainerScreen.hasControlDown();

        // 预览：始终 null（绿色按钮），由服务端处理实际缺失
        if (!doTransfer) {
            return null;
        }

        // 构建模板物品列表（9 格，对应 3x3 合成格）
        var templateItems = NonNullList.withSize(9, ItemStack.EMPTY);
        var inputs = recipe.inputs();
        for (int i = 0; i < templateItems.size() && i < inputs.size(); i++) {
            var variants = inputs.get(i);
            if (variants != null && !variants.isEmpty()) {
                templateItems.set(i, variants.get(0).copyWithCount(1));
            }
        }

        // 奥术配方不在 vanilla 配方管理器中，recipeId 传 null
        // 服务端会用 Ingredient.of(templateItems) 重建，然后从 ME 网络提取
        var packet = new FillCraftingGridFromRecipePacket(null, templateItems, craftMissing);
        ThaumicEnergistics.LOG.info("[JEI-Arcane] Sending FillCraftingGridFromRecipePacket, templateCount={}",
                templateItems.stream().filter(s -> !s.isEmpty()).count());
        PacketDistributor.sendToServer(packet);

        return null;
    }
}
