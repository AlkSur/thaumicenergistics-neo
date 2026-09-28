package tc_energy.integration.jei;

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
import appeng.api.stacks.AEItemKey;
import appeng.util.CraftingRecipeUtil;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.world.inventory.MenuType;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.container.ContainerArcaneCraftingTerminal;
import tc_energy.init.ModMenuTypes;

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
        // 用 JEI 官方映射 helper.getGuiSlotIndexToIngredientMap：配方居中摆入 3x3 网格，
        // 键空间与 JEI 输入槽视图完全一致。之前用 ensure3by3CraftingMatrix（左上对齐填充），
        // 对非 3x3 配方（如 2x2 笔与墨）索引错位，导致缺失高亮画到错误的格子上。
        var slotToIngredientMap = helper.getGuiSlotIndexToIngredientMap(recipeHolder);
        var missingSlots = menu.findMissingIngredients(slotToIngredientMap);

        // 全部缺失 → 红色，无法点击
        if (missingSlots.missingSlots().size() == slotToIngredientMap.size()) {
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
                // 有缺料 → USER_FACING 阻止转移（按钮不可点）；仅可合成（craftable）不阻止（可触发自动合成）
                return new MissingSlotsError(missingSlots, craftMissing, color, missingSlots.anyMissing(), menu);
            }
            // 全部物品都有 → null = 绿色按钮
            return null;
        }

        // 实际转移：发送 ME 网络填充包
        // 模板按可用变体挑选（网络有货→背包有货→可合成→首个），
        // 否则固定取 items[0]（如石头），网络里只有深板岩/圆石时槽位会拉不到东西。
        var templateItems = NonNullList.withSize(9, ItemStack.EMPTY);
        var ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        var stored = menu.snapshotStoredAmounts();
        var craftable = menu.snapshotCraftableKeys();
        var reserved = new Object2IntOpenHashMap<AEItemKey>();
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            var ingredient = ingredients.get(i);
            if (!ingredient.isEmpty()) {
                templateItems.set(i, menu.pickAvailableTemplate(ingredient, stored, craftable, reserved));
            }
        }

        @Nullable
        ResourceLocation recipeId = recipeHolder.id();
        if (recipeId != null && player.level().getRecipeManager().byKey(recipeId).isEmpty()) {
            recipeId = null;
        }

        var packet = new FillCraftingGridFromRecipePacket(recipeId, templateItems, craftMissing);
        PacketDistributor.sendToServer(packet);

        return null;
    }

    /**
     * 缺失/可合成物品的预览错误渲染器。
     * 参考 AE2-JEI-Integration.UseCraftingRecipeTransfer.ErrorRenderer。
     * 供奥术配方转移（ThEUseArcaneRecipeTransfer）复用。
     * blockTransfer=true 时返回 ERROR（阻止转移，按钮灰/橙不可点）。
     */
    public record MissingSlotsError(
            ContainerArcaneCraftingTerminal.MissingIngredientSlots indices,
            boolean craftMissing,
            int color,
            boolean blockTransfer,
            ContainerArcaneCraftingTerminal menu) implements IRecipeTransferError {

        @Override
        public Type getType() {
            // USER_FACING 阻止转移（allowsTransfer=false）且显示错误；COSMETIC 仅预览
            return blockTransfer ? Type.USER_FACING : Type.COSMETIC;
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

            // 用 JEI 槽视图 drawHighlight 画高亮（AE2-JEI-Integration 官方做法）。
            // 不能用 menu.slots 的容器坐标：奥术终端槽位顺序与配方格不对应，
            // 且 translate(recipeX,recipeY) 后再用相对坐标会双重偏移 → 红框画到屏幕外。
            var inputSlots = slots.getSlotViews(RecipeIngredientRole.INPUT);
            for (int i = 0; i < 9; i++) {
                boolean missing = indices.missingSlots().contains(i);
                boolean craftable = indices.craftableSlots().contains(i);
                if (!missing && !craftable) continue;
                if (i >= inputSlots.size()) continue;
                var slotView = inputSlots.get(i);
                slotView.drawHighlight(guiGraphics,
                        missing ? RED_SLOT_HIGHLIGHT_COLOR : BLUE_SLOT_HIGHLIGHT_COLOR);
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
                        "tc_energy.jei.transfer.missing", missing));
            }
            if (craftable > 0) {
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "tc_energy.jei.transfer.craftable", craftable));
            }
            if (craftMissing) {
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "tc_energy.jei.transfer.ctrl_click"));
            }
        }
    }
}
