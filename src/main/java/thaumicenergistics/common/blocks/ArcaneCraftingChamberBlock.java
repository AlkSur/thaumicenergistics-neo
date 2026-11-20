package thaumicenergistics.common.blocks;

import appeng.api.implementations.items.IMemoryCard;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;
import thaumicenergistics.common.container.ContainerArcaneCraftingChamber;
import thaumicenergistics.common.tiles.TileArcaneCraftingChamber;

/** The arcane crafting chamber of one ECO tier (4, 6 or 9). A memory card sets its vis source, as on the assembler. */
public class ArcaneCraftingChamberBlock extends NetworkStatusEntityBlock {
    private final int tier;
    private final Supplier<? extends BlockEntityType<?>> type;

    public ArcaneCraftingChamberBlock(Properties properties, int tier, Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.tier = tier;
        this.type = type;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileArcaneCraftingChamber(type.get(), pos, state, tier, this);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TileArcaneCraftingChamber tile)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof IMemoryCard memoryCard) {
            if (!level.isClientSide) tile.onMemoryCardActivate(player, memoryCard, stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide) {
            ((IPlayerExtension) player).openMenu(state.getMenuProvider(level, pos), pos);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TileArcaneCraftingChamber tile)) return null;
        return new SimpleMenuProvider((id, inv, p) -> new ContainerArcaneCraftingChamber(id, inv, tile), getName());
    }
}
