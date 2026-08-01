package thaumicenergistics.integration.jei;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import org.jetbrains.annotations.Nullable;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.util.CraftingRecipeUtil;

import net.minecraft.world.inventory.MenuType;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.init.ModMenuTypes;

import static appeng.integration.modules.itemlists.TransferHelper.BLUE_PLUS_BUTTON_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.ORANGE_PLUS_BUTTON_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.RED_SLOT_HIGHLIGHT_COLOR;

/**
 * 奥术合成终端专用的 JEI 配方转移处理器。
 * 预览时使用 findMissingIngredients 检查 ME 网络物品可用性，
 * 实际转移时发送 FillCraftingGridFromRecipePacket 从 ME 网络填充材料。
 *
 * 参考 AE2-JEI-Integration 的 UseCraftingRecipeTransfer 实现。
 */
public class ThEUseCraftingRecipeTransfer
        implements IRecipeTransferHandler<ContainerArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

    private static final int CRAFTING_GRID_WIDTH = 3;
    private static final int CRAFTING_GRID_HEIGHT = 3;

    private final IRecipeTransferHandlerHelper helper;

    public ThEUseCraftingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
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
    public mezz.jei.api.recipe.RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return mezz.jei.api.constants.RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(
            ContainerArcaneCraftingTerminal menu,
            RecipeHolder<CraftingRecipe> recipeHolder,
            IRecipeSlotsView display,
            net.minecraft.world.entity.player.Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        var recipe = recipeHolder.value();
        ThaumicEnergistics.LOG.info("[JEI] transferRecipe called, recipeId={}, doTransfer={}, player={}",
                recipeHolder.id(), doTransfer, player.getName().getString());
        if (recipe.getType() != RecipeType.CRAFTING) {
            ThaumicEnergistics.LOG.warn("[JEI] Not a crafting recipe, type={}", recipe.getType());
            return helper.createInternalError();
        }
        if (recipe.getIngredients().isEmpty()) {
            return helper.createInternalError();
        }
        if (!recipe.canCraftInDimensions(CRAFTING_GRID_WIDTH, CRAFTING_GRID_HEIGHT)) {
            return helper.createInternalError();
        }

        boolean craftMissing = AbstractContainerScreen.hasControlDown();
        // 自行构建 slot→ingredient 映射（0-8 对应 3x3 合成格），
        // 不依赖 helper.getGuiSlotIndexToIngredientMap（需要 IRecipeTransferInfo 才能正确工作）
        var slotToIngredientMap = buildSlotToIngredientMap(recipe);
        var missingSlots = menu.findMissingIngredients(slotToIngredientMap);
        ThaumicEnergistics.LOG.info("[JEI] missingSlots: missing={}, craftable={}, total={}",
                missingSlots.missingSlots().size(), missingSlots.craftableSlots().size(), missingSlots.totalSize());

        // 全部缺失 → 红色，无法点击
        if (missingSlots.missingSlots().size() == slotToIngredientMap.size()) {
            ThaumicEnergistics.LOG.info("[JEI] All items missing, returning NO_ITEMS error");
            var inputSlots = display.getSlotViews(RecipeIngredientRole.INPUT);
            var missingSlotViews = missingSlots.missingSlots().stream()
                    .map(idx -> idx < inputSlots.size() ? inputSlots.get(idx) : null)
                    .filter(Objects::nonNull)
                    .toList();
            return helper.createUserErrorForMissingSlots(
                    appeng.core.localization.ItemModText.NO_ITEMS.text(), missingSlotViews);
        }

        // 预览：检查缺失情况，返回对应的错误渲染
        if (!doTransfer) {
            if (missingSlots.totalSize() != 0) {
                int color = missingSlots.anyMissing() ? ORANGE_PLUS_BUTTON_COLOR : BLUE_PLUS_BUTTON_COLOR;
                return new MissingSlotsError(missingSlots, craftMissing, color);
            }
            // 全部物品都有 → null = 绿色按钮
            return null;
        }

        // 实际转移：发送 ME 网络填充包
        var templateItems = NonNullList.withSize(9, ItemStack.EMPTY);
        var ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            var ingredient = ingredients.get(i);
            if (!ingredient.isEmpty()) {
                var items = ingredient.getItems();
                if (items.length > 0) {
                    templateItems.set(i, items[0].copyWithCount(1));
                }
            }
        }

        @Nullable
        ResourceLocation recipeId = recipeHolder.id();
        if (recipeId != null && player.level().getRecipeManager().byKey(recipeId).isEmpty()) {
            recipeId = null;
        }

        var packet = new FillCraftingGridFromRecipePacket(recipeId, templateItems, craftMissing);
        ThaumicEnergistics.LOG.info("[JEI] Sending FillCraftingGridFromRecipePacket, recipeId={}, templateCount={}, craftMissing={}",
                recipeId, templateItems.stream().filter(s -> !s.isEmpty()).count(), craftMissing);
        PacketDistributor.sendToServer(packet);

        return null;
    }

    /**
     * 根据配方构建 3x3 合成格的 slot index → ingredient 映射。
     * 使用 CraftingRecipeUtil.ensure3by3CraftingMatrix 确保 9 格对齐，
     * 与实际转移时的索引体系完全一致。
     */
    private static Map<Integer, Ingredient> buildSlotToIngredientMap(CraftingRecipe recipe) {
        var ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        Map<Integer, Ingredient> map = new HashMap<>();
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            var ing = ingredients.get(i);
            if (!ing.isEmpty()) {
                map.put(i, ing);
            }
        }
        return map;
    }

    /**
     * 缺失/可合成物品的预览错误渲染器。
     * 参考 AE2-JEI-Integration.UseCraftingRecipeTransfer.ErrorRenderer。
     */
    private record MissingSlotsError(
            ContainerArcaneCraftingTerminal.MissingIngredientSlots indices,
            boolean craftMissing,
            int color) implements IRecipeTransferError {

        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return color;
        }

        @Override
        public void showError(GuiGraphics guiGraphics, int mouseX, int mouseY,
                IRecipeSlotsView slots, int recipeX, int recipeY) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(recipeX, recipeY, 0);

            var slotViews = slots.getSlotViews(RecipeIngredientRole.INPUT);
            for (int i = 0; i < slotViews.size(); i++) {
                var slotView = slotViews.get(i);
                boolean missing = indices.missingSlots().contains(i);
                boolean craftable = indices.craftableSlots().contains(i);
                if (missing || craftable) {
                    slotView.drawHighlight(
                            guiGraphics,
                            missing ? RED_SLOT_HIGHLIGHT_COLOR : BLUE_SLOT_HIGHLIGHT_COLOR);
                }
            }

            poseStack.popPose();
        }

        @Override
        public int getMissingCountHint() {
            return indices.missingSlots().size();
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            int missing = indices.missingSlots().size();
            int craftable = indices.craftableSlots().size();
            if (missing > 0) {
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "jei.tooltip.transfer.missing", missing));
            }
            if (craftable > 0) {
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "jei.tooltip.transfer.craftable", craftable));
            }
            if (craftMissing) {
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "jei.tooltip.transfer.ctrl.click"));
            }
        }
    }
}
