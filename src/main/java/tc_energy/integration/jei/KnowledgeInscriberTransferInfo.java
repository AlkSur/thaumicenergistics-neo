package tc_energy.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import tc_energy.integration.jei.ThEArcaneJeiBridge;
import tc_energy.common.container.ContainerKnowledgeInscriber;
import tc_energy.common.container.slot.GhostSlot;
import tc_energy.init.ModMenuTypes;

/**
 * 知识记录仪 JEI 奥术配方转移槽位映射（让 JEI 显示 "+" 按钮）。
 * recipeSlots = 3×3 ghost 合成网格；inventorySlots = 玩家背包。
 */
public class KnowledgeInscriberTransferInfo
        implements IRecipeTransferInfo<ContainerKnowledgeInscriber, Object> {

    @Override
    public Class<? extends ContainerKnowledgeInscriber> getContainerClass() {
        return ContainerKnowledgeInscriber.class;
    }

    @Override
    public Optional<MenuType<ContainerKnowledgeInscriber>> getMenuType() {
        return Optional.of(ModMenuTypes.KNOWLEDGE_INSCRIBER.get());
    }

    @Override
    public RecipeType<Object> getRecipeType() {
        return ThEArcaneJeiBridge.ARCANE;
    }

    @Override
    public boolean canHandle(ContainerKnowledgeInscriber menu, Object recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ContainerKnowledgeInscriber menu, Object recipe) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot instanceof GhostSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

    @Override
    public List<Slot> getInventorySlots(ContainerKnowledgeInscriber menu, Object recipe) {
        // 玩家背包槽先于机器槽加入（0-35）
        return menu.slots.subList(0, 36);
    }
}
