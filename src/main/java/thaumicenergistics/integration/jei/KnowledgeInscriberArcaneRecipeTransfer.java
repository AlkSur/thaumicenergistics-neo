package thaumicenergistics.integration.jei;

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

import thaumcraft.integration.jei.JeiArcaneRecipe;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.common.container.ContainerKnowledgeInscriber;
import thaumicenergistics.common.container.slot.GhostSlot;
import thaumicenergistics.common.network.KnowledgeInscriberGhostSlotPacket;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 知识记录仪 JEI 奥术配方 "+" 一键放置：
 * 点 "+" → 把配方材料填入 3×3 ghost 合成网格（有序按位置 / 无序填前 N 格）。
 * 复用 KnowledgeInscriberGhostSlotPacket 同步服务端（客户端同时刷新产物）。
 */
public class KnowledgeInscriberArcaneRecipeTransfer
        implements IRecipeTransferHandler<ContainerKnowledgeInscriber, JeiArcaneRecipe> {

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
    public RecipeType<JeiArcaneRecipe> getRecipeType() {
        return ThaumcraftJeiPlugin.ARCANE;
    }

    @Override
    public IRecipeTransferError transferRecipe(
            ContainerKnowledgeInscriber menu,
            JeiArcaneRecipe recipe,
            mezz.jei.api.gui.ingredient.IRecipeSlotsView display,
            net.minecraft.world.entity.player.Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        // 预览：始终可转移（ghost 槽不消耗物品）
        if (!doTransfer) {
            return null;
        }

        // 构建 9 格模板（有序配方 inputs 为 3×3 展开含空位；无序配方为压缩列表——填前 N 格）
        NonNullList<ItemStack> template = NonNullList.withSize(9, ItemStack.EMPTY);
        List<List<ItemStack>> inputs = recipe.inputs();
        for (int i = 0; i < template.size() && i < inputs.size(); i++) {
            List<ItemStack> variants = inputs.get(i);
            if (variants != null && !variants.isEmpty()) {
                template.set(i, variants.get(0).copyWithCount(1));
            }
        }

        // 按 ghost 槽顺序填入 + C2S 同步服务端 + 客户端刷新产物
        List<GhostSlot> ghostSlots = menu.slots.stream()
                .filter(s -> s instanceof GhostSlot)
                .map(s -> (GhostSlot) s)
                .toList();
        for (int i = 0; i < template.size(); i++) {
            ItemStack stack = template.get(i);
            if (stack.isEmpty() || i >= ghostSlots.size()) continue;
            ghostSlots.get(i).set(stack);
            PacketDistributor.sendToServer(
                    new KnowledgeInscriberGhostSlotPacket(menu.containerId, i, stack));
        }
        menu.onCraftingChangedClient();

        return null;
    }
}
