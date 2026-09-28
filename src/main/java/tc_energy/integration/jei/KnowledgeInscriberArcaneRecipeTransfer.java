package tc_energy.integration.jei;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import net.neoforged.neoforge.network.PacketDistributor;

import tc_energy.common.container.ContainerKnowledgeInscriber;
import tc_energy.common.network.KnowledgeInscriberGhostSlotPacket;
import tc_energy.init.ModMenuTypes;

/**
 * 知识记录仪 JEI 奥术配方 "+" 一键放置（Ren 版 ARCANE 分类，反射桥）：
 * 点"+" → 依次发送 9 个 KnowledgeInscriberGhostSlotPacket（网格索引 0-8）。
 * 客户端不直接改本地槽位 —— 服务端 setGhostSlotOnServer 写 craftingContainer +
 * craftingInventory 双容器并触发 updateRecipe，随后容器同步自然回显到 GUI。
 * 有序配方 inputs 为 3×3 展开含空位；无序配方压缩列表填前 N 格。
 */
public class KnowledgeInscriberArcaneRecipeTransfer
        implements IRecipeTransferHandler<ContainerKnowledgeInscriber, Object> {

    @SuppressWarnings("unused")
    private final IRecipeTransferHandlerHelper helper;

    public KnowledgeInscriberArcaneRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

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
    public IRecipeTransferError transferRecipe(
            ContainerKnowledgeInscriber menu,
            Object recipe,
            mezz.jei.api.gui.ingredient.IRecipeSlotsView display,
            net.minecraft.world.entity.player.Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        // 预览：ghost 槽不消耗物品，始终允许
        if (!doTransfer) {
            return null;
        }

        // 构建 9 格模板（JeiBridge.ingredients 返回 List<Ingredient>，按网格序）
        NonNullList<ItemStack> template = NonNullList.withSize(9, ItemStack.EMPTY);
        List<net.minecraft.world.item.crafting.Ingredient> inputs = ThEArcaneJeiBridge.ingredients(recipe);
        for (int i = 0; i < template.size() && i < inputs.size(); i++) {
            var ingredient = inputs.get(i);
            if (ingredient != null) {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks.length > 0 && !stacks[0].isEmpty()) {
                    template.set(i, stacks[0].copyWithCount(1));
                }
            }
        }

        // 逐格发 C2S（网格索引 0-8）；服务端写双容器 + updateRecipe + 容器同步回显
        for (int i = 0; i < template.size(); i++) {
            PacketDistributor.sendToServer(
                    new KnowledgeInscriberGhostSlotPacket(menu.containerId, i, template.get(i)));
        }

        return null;
    }
}
