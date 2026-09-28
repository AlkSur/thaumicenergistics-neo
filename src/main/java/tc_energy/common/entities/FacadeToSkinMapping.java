package tc_energy.common.entities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * AE2 Facade → 背包皮肤映射。
 * 1.7.10 通过 ItemFacade.getBlock()/getMeta() 判断；1.21.1 解析 Facade 物品的底层方块。
 * 支持：Thaumium/Tallow/Wood/Flesh/Stone/Straw/Clay/Iron/Gold/Diamond（与 1.7.10 一致）。
 */
public final class FacadeToSkinMapping {

    private FacadeToSkinMapping() {}

    /**
     * 尝试从物品栈推断对应的背包皮肤。
     *
     * 接受 AE2 Facade 物品或直接的方块物品。
     *
     * @param stack 玩家手持的物品
     * @return 对应的皮肤，若不支持则返回 null
     */
    public static BackpackSkins getSkinFromFacade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Block block = resolveFacadeBlock(stack);
        if (block == null) {
            return null;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);

        // Thaumcraft 方块映射
        if (blockId.getNamespace().equals("thaumcraft")) {
            String path = blockId.getPath();
            if (path.contains("thaumium") || path.contains("cosmetic_solid")) {
                return BackpackSkins.Thaumium;
            }
            if (path.contains("tallow")) return BackpackSkins.Tallow;
            if (path.contains("greatwood") || path.contains("magical_log")) return BackpackSkins.Wood;
            if (path.contains("flesh") || path.contains("taint")) return BackpackSkins.Flesh;
        }

        // 原版方块映射
        if (block == Blocks.STONE) return BackpackSkins.Stone;
        if (block == Blocks.HAY_BLOCK) return BackpackSkins.Straw;
        if (block == Blocks.BRICKS) return BackpackSkins.Clay;
        if (block == Blocks.IRON_BLOCK) return BackpackSkins.Iron;
        if (block == Blocks.GOLD_BLOCK) return BackpackSkins.Gold;
        if (block == Blocks.DIAMOND_BLOCK) return BackpackSkins.Diamond;

        return null;
    }

    /**
     * 从物品栈解析对应方块：BlockItem 或 AE2 Facade（反射接口）。
     */
    private static Block resolveFacadeBlock(ItemStack stack) {
        // 1) 直接是 BlockItem
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }

        // 2) AE2 Facade 物品：尝试通过 IFacadeItem 接口获取纹理方块
        try {
            if (stack.getItem() instanceof appeng.items.parts.FacadeItem facadeItem) {
                net.minecraft.world.level.block.state.BlockState state = facadeItem.getTextureBlockState(stack);
                if (state != null) {
                    return state.getBlock();
                }
            }
        } catch (Throwable ignored) {
            // AE2 API 版本可能不兼容，静默忽略
        }

        return null;
    }
}
