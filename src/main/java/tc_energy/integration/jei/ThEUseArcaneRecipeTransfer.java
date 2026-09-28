package tc_energy.integration.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
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
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.neoforged.neoforge.network.PacketDistributor;

import tc_energy.common.container.ContainerArcaneCraftingTerminal;
import tc_energy.init.ModMenuTypes;

import static appeng.integration.modules.itemlists.TransferHelper.BLUE_PLUS_BUTTON_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.ORANGE_PLUS_BUTTON_COLOR;
import static appeng.integration.modules.itemlists.TransferHelper.RED_SLOT_HIGHLIGHT_COLOR;

/**
 * 奥术合成终端专用的 JEI 奥术配方转移处理器（Ren 版 ARCANE 分类，反射桥）。
 * 点击 "+" 时发送 FillCraftingGridFromRecipePacket 从 ME 网络填充材料。
 */
public class ThEUseArcaneRecipeTransfer
        implements IRecipeTransferHandler<ContainerArcaneCraftingTerminal, Object> {

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
    public mezz.jei.api.recipe.RecipeType<Object> getRecipeType() {
        return ThEArcaneJeiBridge.ARCANE;
    }

    @Override
    public IRecipeTransferError transferRecipe(
            ContainerArcaneCraftingTerminal menu,
            Object recipe,
            IRecipeSlotsView display,
            net.minecraft.world.entity.player.Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        boolean craftMissing = AbstractContainerScreen.hasControlDown();

        // 关键：槽位映射必须以 JEI 展示的 INPUT 槽视图为准。
        // 端口 ArcaneRecipeCategory 已把配方铺成 3×3 网格（pattern 左上对齐，空格子=空槽），
        // 且"法杖组装"是 linked 变体槽（悬停哪套杖端/杖芯，展示就是哪套）。
        // 旧实现按 recipe.ingredients() 紧凑列表做 i→槽位 i：① 宽度<3 的配方会错位（格子空/误报缺少）；
        // ② 法杖组装只会填第一套变体；③ tag 材料不展开。
        var inputSlots = display.getSlotViews(RecipeIngredientRole.INPUT);
        var slotIngredients = new ArrayList<Ingredient>(9);
        var slotToIngredientMap = new HashMap<Integer, Ingredient>();
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = Ingredient.EMPTY;
            if (i < inputSlots.size()) {
                var stacks = inputSlots.get(i).getItemStacks().toList();
                if (!stacks.isEmpty()) {
                    ingredient = Ingredient.of(stacks.toArray(ItemStack[]::new));
                }
            }
            slotIngredients.add(ingredient);
            if (!ingredient.isEmpty()) {
                slotToIngredientMap.put(i, ingredient);
            }
        }
        var missingSlots = menu.findMissingIngredients(slotToIngredientMap);

        // 全部缺失 → 红色，无法点击
        if (missingSlots.missingSlots().size() == slotToIngredientMap.size()) {
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
                return new ThEUseCraftingRecipeTransfer.MissingSlotsError(
                        missingSlots, craftMissing, color, missingSlots.anyMissing(), menu);
            }
            return null;
        }

        // 构建模板物品列表（9 格，对应 3x3 合成格）——按当前展示的变体挑可用材料
        var templateItems = NonNullList.withSize(9, ItemStack.EMPTY);
        var stored = menu.snapshotStoredAmounts();
        var craftable = menu.snapshotCraftableKeys();
        var reserved = new Object2IntOpenHashMap<AEItemKey>();
        for (int i = 0; i < 9; i++) {
            var ingredient = slotIngredients.get(i);
            if (!ingredient.isEmpty()) {
                templateItems.set(i, menu.pickAvailableTemplate(ingredient, stored, craftable, reserved));
            }
        }

        // 奥术配方不在 vanilla 配方管理器中，recipeId 传 null；
        // 服务端用 Ingredient.of(templateItems) 重建，然后从 ME 网络提取
        var packet = new FillCraftingGridFromRecipePacket(null, templateItems, craftMissing);
        PacketDistributor.sendToServer(packet);

        return null;
    }


}
