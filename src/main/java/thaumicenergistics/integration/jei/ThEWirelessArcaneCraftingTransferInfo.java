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
 * 无线奥术合成终端的 JEI 槽位映射（ThE 奥术配方）：与有线版唯一差异是 MenuType。
 */
public class ThEWirelessArcaneCraftingTransferInfo
        implements IRecipeTransferInfo<ContainerArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

    @Override
    public Class<? extends ContainerArcaneCraftingTerminal> getContainerClass() {
        return ContainerArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<ContainerArcaneCraftingTerminal>> getMenuType() {
        return Optional.of(ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
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
