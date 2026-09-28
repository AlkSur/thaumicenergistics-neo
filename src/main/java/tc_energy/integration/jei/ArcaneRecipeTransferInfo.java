package tc_energy.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import appeng.menu.SlotSemantics;
import tc_energy.integration.jei.ThEArcaneJeiBridge;
import tc_energy.common.container.ContainerArcaneCraftingTerminal;
import tc_energy.init.ModMenuTypes;

/**
 * JEI 配方转移信息：支持 TC4 奥术配方（JeiArcaneRecipe）。
 * 与 ArcaneCraftingTransferInfo（vanilla 合成）配合使用。
 */
public class ArcaneRecipeTransferInfo implements IRecipeTransferInfo<ContainerArcaneCraftingTerminal, Object> {

    @Override
    public Class<? extends ContainerArcaneCraftingTerminal> getContainerClass() {
        return ContainerArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<ContainerArcaneCraftingTerminal>> getMenuType() {
        return Optional.of(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get());
    }

    @Override
    public RecipeType<Object> getRecipeType() {
        return ThEArcaneJeiBridge.ARCANE;
    }

    @Override
    public boolean canHandle(ContainerArcaneCraftingTerminal menu, Object recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ContainerArcaneCraftingTerminal menu, Object recipe) {
        return menu.getSlots(SlotSemantics.CRAFTING_GRID);
    }

    @Override
    public List<Slot> getInventorySlots(ContainerArcaneCraftingTerminal menu, Object recipe) {
        List<Slot> inventorySlots = new ArrayList<>();
        var playerInv = menu.getPlayerInventory();
        for (Slot slot : menu.slots) {
            if (slot != null && slot.container == playerInv) {
                inventorySlots.add(slot);
            }
        }
        return inventorySlots;
    }
}
