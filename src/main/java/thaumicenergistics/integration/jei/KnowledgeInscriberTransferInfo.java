package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import thaumcraft.integration.jei.JeiArcaneRecipe;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.common.container.ContainerKnowledgeInscriber;
import thaumicenergistics.common.container.slot.GhostSlot;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 知识记录仪 JEI 奥术配方转移槽位映射（让 JEI 显示 "+" 按钮）。
 * recipeSlots = 3×3 ghost 合成网格；inventorySlots = 玩家背包。
 */
public class KnowledgeInscriberTransferInfo
        implements IRecipeTransferInfo<ContainerKnowledgeInscriber, JeiArcaneRecipe> {

    @Override
    public Class<? extends ContainerKnowledgeInscriber> getContainerClass() {
        return ContainerKnowledgeInscriber.class;
    }

    @Override
    public Optional<MenuType<ContainerKnowledgeInscriber>> getMenuType() {
        return Optional.of(ModMenuTypes.KNOWLEDGE_INSCRIBER.get());
    }

    @Override
    public RecipeType<JeiArcaneRecipe> getRecipeType() {
        return ThaumcraftJeiPlugin.ARCANE;
    }

    @Override
    public boolean canHandle(ContainerKnowledgeInscriber menu, JeiArcaneRecipe recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ContainerKnowledgeInscriber menu, JeiArcaneRecipe recipe) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot instanceof GhostSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

    @Override
    public List<Slot> getInventorySlots(ContainerKnowledgeInscriber menu, JeiArcaneRecipe recipe) {
        // 玩家背包槽先于机器槽加入（0-35）
        return menu.slots.subList(0, 36);
    }
}
