package thaumicenergistics.integration.jei;

import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 为奥术合成终端提供 JEI 配方转移的槽位映射。
 * 让 JEI 知道哪些槽是合成格（CRAFTING_GRID）、哪些是玩家背包。
 * 实际转移走网络由 ThEUseCraftingRecipeTransfer 处理。
 */
public class ArcaneCraftingTransferInfo
        implements IRecipeTransferInfo<ContainerArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

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
        return RecipeTypes.CRAFTING;
    }

    @Override
    public boolean canHandle(ContainerArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ContainerArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return menu.getSlots(appeng.menu.SlotSemantics.CRAFTING_GRID);
    }

    @Override
    public List<Slot> getInventorySlots(ContainerArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return menu.getSlots(appeng.menu.SlotSemantics.PLAYER_INVENTORY);
    }
}
