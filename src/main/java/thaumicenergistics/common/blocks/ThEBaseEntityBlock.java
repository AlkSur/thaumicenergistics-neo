package thaumicenergistics.common.blocks;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import thaumicenergistics.common.tiles.TileEssentiaProvider;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;
import thaumicenergistics.common.tiles.TileInfusionProvider;
import thaumicenergistics.common.tiles.ThETileBase;

public abstract class ThEBaseEntityBlock extends Block implements EntityBlock {
    protected ThEBaseEntityBlock(Properties p) { super(p); }

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == thaumicenergistics.init.ModBlockEntities.ESSENTIA_PROVIDER.get()) {
            return createTicker((lvl, pos, st, be) -> ((TileEssentiaProvider) be).serverTick());
        }
        if (type == thaumicenergistics.init.ModBlockEntities.INFUSION_PROVIDER.get()) {
            return createTicker((lvl, pos, st, be) -> ((TileInfusionProvider) be).serverTick());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityTicker<T> createTicker(BlockEntityTicker<? super T> ticker) {
        return (BlockEntityTicker<T>) ticker;
    }

    /**
     * AE2 扳手拆卸。WrenchHook 只处理 AEBaseBlockEntity，ThE 方块 BE 不走该路径，故在此自行拆卸并掉落。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // 扳手拆卸由 ThEWrenchHandler（RightClickBlock 事件）处理，这里不重复
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 挖掘掉落方块自身（不导出方块实体 NBT）。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ThETileBase tb) tb.onBreakBlock();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
