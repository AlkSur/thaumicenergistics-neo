package thaumicenergistics.integration.jei;

import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import appeng.menu.SlotSemantics;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.init.ModMenuTypes;

/**
 * JEI 配方转移信息：告诉 JEI 哪些槽位是合成网格，哪些是玩家背包。
 * 这样 JEI 的 "+" 按钮就能将配方转移到奥术合成台。
 */
public class ThEArcaneCraftingTransferInfo implements IRecipeTransferInfo<ContainerArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

    @Override
    public Class<? extends ContainerArcaneCraftingTerminal> getContainerClass() {
        return ContainerArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<ContainerArcaneCraftingTerminal>> getMenuType() {
        return Optional.of(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public boolean canHandle(ContainerArcaneCraftingTerminal container, RecipeHolder<CraftingRecipe> recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ContainerArcaneCraftingTerminal container, RecipeHolder<CraftingRecipe> recipe) {
        return container.getSlots(SlotSemantics.CRAFTING_GRID);
    }

    @Override
    public List<Slot> getInventorySlots(ContainerArcaneCraftingTerminal container, RecipeHolder<CraftingRecipe> recipe) {
        return container.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }
}
