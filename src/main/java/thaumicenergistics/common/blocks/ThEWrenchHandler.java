package thaumicenergistics.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumicenergistics.ThaumicEnergistics;
import appeng.datagen.providers.tags.ConventionTags;

/**
 * AE2 扳手拆卸处理。AE2 的 WrenchHook 只处理 AEBaseBlockEntity，ThE 方块 BE 不走该路径；
 * 这里用与 WrenchHook 同级的 RightClickBlock 事件，在 Block.useItemOn 之前拦截扳手右键，
 * 对 ThE 方块执行拆卸（掉落 + 移除）。
 */
public final class ThEWrenchHandler {

    private ThEWrenchHandler() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(ThEWrenchHandler.class);
    }

    /** 判定目标是否为本模组可拆卸方块（按命名空间判断，兼容未继承 ThETileBase 的工作台等）。 */
    public static boolean isDisassemblyTarget(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return ThaumicEnergistics.MODID
                .equals(state.getBlock().builtInRegistryHolder().key().location().getNamespace());
    }

    /**
     * ThE 方块扳手拆卸公共入口（普通 AE2 扳手事件与法杖 AE 扳手核心共用）：
     * 服务端掉落方块物品并移除方块，客户端播放拆卸音效。
     * 返回 true 表示目标为 ThE 方块且拆卸已执行。
     */
    public static boolean disassembleThEBlock(Player player, Level level, BlockPos pos) {
        if (!isDisassemblyTarget(level, pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        if (!level.isClientSide) {
            // ThE 方块掉落即自身（挖掘走 getDrops 覆写；扳手拆卸直接给方块物品，避免 loot table 为空）
            player.getInventory().placeItemBackInInventory(new ItemStack(be.getBlockState().getBlock()));
            level.removeBlock(pos, false);
        } else {
            level.playSound(player, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return true;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        var player = event.getEntity();
        if (player.isSpectator()) {
            return;
        }
        var stack = player.getItemInHand(event.getHand());
        if (!player.isShiftKeyDown() || !stack.is(ConventionTags.WRENCH)) {
            return;
        }

        var level = event.getLevel();
        BlockPos pos = event.getPos();

        if (!disassembleThEBlock(player, level, pos)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
