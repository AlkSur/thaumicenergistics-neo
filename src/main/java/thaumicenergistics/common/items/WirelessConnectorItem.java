package thaumicenergistics.common.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.common.tiles.TileEssentiaProvider;
import thaumicenergistics.common.tiles.TileEssentiaProviderConnection;

/**
 * 源质无线绑定工具：
 * - Shift+右键：绑定/断开（未连接的接收器=选中；供应器=绑定选中的接收器；已连接的接收器=断开）
 * - 右键：显示连接信息（聊天栏，坐标可复制）
 */
public class WirelessConnectorItem extends Item {

    private static final String KEY_SELECTED_DIMENSION = "SelectedDimension";

    /** True when nothing is selected, or the selection was made in this dimension (old selections count as here). */
    private static boolean sameDimension(CompoundTag tag, Level level) {
        if (!tag.contains(KEY_SELECTED_DIMENSION)) return true;
        return tag.getString(KEY_SELECTED_DIMENSION).equals(level.dimension().location().toString());
    }

    private static final String KEY_SELECTED_RECEIVER = "SelectedReceiver";
    private static final String KEY_SELECTED_PROVIDER = "SelectedProvider";

    public WirelessConnectorItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag getSelectionTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setSelectionTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelection(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    private static void showReceiverInfo(Player player, TileEssentiaProviderConnection receiver) {
        BlockPos lp = receiver.getLinkedProviderPos();
        if (lp != null) {
            player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.receiver_linked", lp.getX(), lp.getY(), lp.getZ()).withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.receiver_unlinked").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void showProviderInfo(Player player, TileEssentiaProvider provider) {
        var list = provider.getLinkedReceivers();
        player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.provider_receivers", list.size(), thaumicenergistics.config.ThEConfig.providerMaxReceivers()).withStyle(ChatFormatting.GRAY));
        if (list.isEmpty()) {
            player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.none").withStyle(ChatFormatting.GRAY));
        } else {
            for (BlockPos rp : list) {
                player.sendSystemMessage(Component.literal("  [" + rp.getX() + ", " + rp.getY() + ", " + rp.getZ() + "]").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        if (player.isShiftKeyDown()) {
            if (be instanceof TileEssentiaProviderConnection receiver) {
                if (receiver.getLinkedProviderPos() != null) {
                    // 已连接 → 断开
                    receiver.clearLink();
                    player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.unlinked").withStyle(ChatFormatting.YELLOW));
                    return InteractionResult.SUCCESS;
                }
                CompoundTag tag = getSelectionTag(stack);
                if (!sameDimension(tag, level)) {
                    // The selection only stores a position; using it in another dimension would touch the
                    // block at the same coordinates there (TEA2-15).
                    clearSelection(stack);
                    player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.other_dimension").withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }
                if (tag.contains(KEY_SELECTED_PROVIDER)) {
                    // 已选中供应器 → 绑定到它
                    BlockPos providerPos = BlockPos.of(tag.getLong(KEY_SELECTED_PROVIDER));
                    var error = receiver.setLinkedProvider(providerPos);
                    if (error == null) {
                        player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.linked").withStyle(ChatFormatting.GREEN));
                    } else {
                        player.sendSystemMessage(error);
                    }
                    clearSelection(stack);
                } else if (tag.contains(KEY_SELECTED_RECEIVER)) {
                    player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.receiver_to_receiver").withStyle(ChatFormatting.RED));
                } else {
                    // 未选中 → 选中该接收器
                    tag.putLong(KEY_SELECTED_RECEIVER, pos.asLong());
                    tag.putString(KEY_SELECTED_DIMENSION, level.dimension().location().toString());
                    setSelectionTag(stack, tag);
                    player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.receiver_selected").withStyle(ChatFormatting.GREEN));
                }
                return InteractionResult.SUCCESS;
            }
            if (be instanceof TileEssentiaProvider) {
                CompoundTag tag = getSelectionTag(stack);
                if (!sameDimension(tag, level)) {
                    clearSelection(stack);
                    player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.other_dimension").withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }
                if (tag.contains(KEY_SELECTED_RECEIVER)) {
                    // 已选中接收器 → 绑定到本供应器
                    BlockPos receiverPos = BlockPos.of(tag.getLong(KEY_SELECTED_RECEIVER));
                    if (level.isLoaded(receiverPos)
                            && level.getBlockEntity(receiverPos) instanceof TileEssentiaProviderConnection receiver) {
                        var error = receiver.setLinkedProvider(pos);
                        if (error == null) {
                            player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.linked").withStyle(ChatFormatting.GREEN));
                        } else {
                            player.sendSystemMessage(error);
                        }
                    } else {
                        player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.receiver_missing").withStyle(ChatFormatting.RED));
                    }
                    clearSelection(stack);
                } else {
                    // 未选中 → 选中该供应器
                    tag.putLong(KEY_SELECTED_PROVIDER, pos.asLong());
                    tag.putString(KEY_SELECTED_DIMENSION, level.dimension().location().toString());
                    setSelectionTag(stack, tag);
                    player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.provider_selected").withStyle(ChatFormatting.GREEN));
                }
                return InteractionResult.SUCCESS;
            }
            // 其他方块：清除选中
            if (getSelectionTag(stack).contains(KEY_SELECTED_RECEIVER)
                    || getSelectionTag(stack).contains(KEY_SELECTED_PROVIDER)) {
                clearSelection(stack);
                player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.cleared").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (be instanceof TileEssentiaProviderConnection receiver) {
            showReceiverInfo(player, receiver);
            return InteractionResult.SUCCESS;
        }
        if (be instanceof TileEssentiaProvider provider) {
            showProviderInfo(player, provider);
            return InteractionResult.SUCCESS;
        }
        // 其他方块：清除选中
        if (getSelectionTag(stack).contains(KEY_SELECTED_RECEIVER)
                || getSelectionTag(stack).contains(KEY_SELECTED_PROVIDER)) {
            clearSelection(stack);
            player.sendSystemMessage(Component.translatable("thaumicenergistics.connector.cleared").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
