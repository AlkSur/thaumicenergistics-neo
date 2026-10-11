package thaumicenergistics.integration.jei;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;


import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.neoforged.neoforge.network.PacketDistributor;

import thaumcraft.integration.jei.JeiArcaneRecipe;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.init.ModMenuTypes;

import static appeng.integration.modules.itemlists.TransferHelper.BLUE_PLUS_BUTTON_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.ORANGE_PLUS_BUTTON_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.RED_SLOT_HIGHLIGHT_COLOR;
import appeng.core.localization.ItemModText;
import net.minecraft.world.entity.player.Player;

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
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        boolean craftMissing = AbstractContainerScreen.hasControlDown();

        // 构建 3x3 合成格 slot→ingredient 映射，检测材料缺失（与普通配方一致）
        var slotToIngredientMap = buildSlotToIngredientMap(recipe);
        var missingSlots = menu.findMissingIngredients(slotToIngredientMap);

        // 全部缺失 → 红色，无法点击
        if (missingSlots.missingSlots().size() == slotToIngredientMap.size()) {
            var inputSlots = display.getSlotViews(RecipeIngredientRole.INPUT);
            var missingSlotViews = missingSlots.missingSlots().stream()
                    .map(idx -> idx < inputSlots.size() ? inputSlots.get(idx) : null)
                    .filter(Objects::nonNull)
                    .toList();
            return helper.createUserErrorForMissingSlots(
                    ItemModText.NO_ITEMS.text(), missingSlotViews);
        }

        // 预览：检查缺失情况，返回对应的错误渲染
        if (!doTransfer) {
            if (missingSlots.totalSize() != 0) {
                int color = missingSlots.anyMissing() ? ORANGE_PLUS_BUTTON_COLOR : BLUE_PLUS_BUTTON_COLOR;
                // 有缺料 → Type.ERROR 阻止转移（按钮不可点），有料也不允许部分填充
                return new ThEUseCraftingRecipeTransfer.MissingSlotsError(
                        missingSlots, craftMissing, color, missingSlots.anyMissing(), menu);
            }
            return null;
        }

        // 构建模板物品列表（9 格，对应 3x3 合成格）——按可用变体挑选：
        // 奥术配方 recipeId=null，服务端只能用客户端模板重建 Ingredient，
        // 固定取 variants.get(0)（如石头）会导致有深板岩/圆石却拉不到。
        var templateItems = NonNullList.withSize(9, ItemStack.EMPTY);
        var inputs = recipe.inputs();
        var stored = menu.snapshotStoredAmounts();
        var craftable = menu.snapshotCraftableKeys();
        var reserved = new Object2IntOpenHashMap<AEItemKey>();
        for (int i = 0; i < templateItems.size() && i < inputs.size(); i++) {
            var variants = inputs.get(i);
            if (variants != null && !variants.isEmpty()) {
                Ingredient ingredient = Ingredient.of(variants.stream());
                templateItems.set(i, menu.pickAvailableTemplate(ingredient, stored, craftable, reserved));
            }
        }

        // 奥术配方不在 vanilla 配方管理器中，recipeId 传 null
        // 服务端会用 Ingredient.of(templateItems) 重建，然后从 ME 网络提取
        var packet = new FillCraftingGridFromRecipePacket(null, templateItems, craftMissing);
        PacketDistributor.sendToServer(packet);

        return null;
    }

    /**
     * 根据奥术配方 inputs（9 槽 List<List<ItemStack>>）构建 3x3 合成格 slot → ingredient 映射。
     */
    private static Map<Integer, Ingredient> buildSlotToIngredientMap(JeiArcaneRecipe recipe) {
        Map<Integer, Ingredient> map = new HashMap<>();
        var inputs = recipe.inputs();
        for (int i = 0; i < inputs.size() && i < 9; i++) {
            var variants = inputs.get(i);
            if (variants != null && !variants.isEmpty()) {
                map.put(i, Ingredient.of(variants.stream()));
            }
        }
        return map;
    }
}
