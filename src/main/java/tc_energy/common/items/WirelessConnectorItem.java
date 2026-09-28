package tc_energy.common.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import tc_energy.common.tiles.TileEssentiaProvider;
import tc_energy.common.tiles.TileEssentiaProviderConnection;
import tc_energy.init.ModBlocks;

/**
 * 源质无线绑定工具：
 * - Shift+右键：绑定/断开（未连接的接收器=选中；供应器=绑定选中的接收器；已连接的接收器=断开）
 * - 右键：显示连接信息（聊天栏，坐标可复制）
 */
public class WirelessConnectorItem extends Item {

    private static final String KEY_SELECTED_RECEIVER = "SelectedReceiver";
    private static final String KEY_SELECTED_PROVIDER = "SelectedProvider";

    public WirelessConnectorItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag getSelectionTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
    }

    private static void setSelectionTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    private static void clearSelection(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    private static void showReceiverInfo(Player player, TileEssentiaProviderConnection receiver) {
        BlockPos lp = receiver.getLinkedProviderPos();
        if (lp != null) {
            player.sendSystemMessage(Component.literal("§7[源质无线接收器] 已连接供应器: §a[" + lp.getX() + ", " + lp.getY() + ", " + lp.getZ() + "]"));
        } else {
            player.sendSystemMessage(Component.literal("§7[源质无线接收器] 未连接任何供应器"));
        }
    }

    private static void showProviderInfo(Player player, TileEssentiaProvider provider) {
        var list = provider.getLinkedReceivers();
        player.sendSystemMessage(Component.literal("§7[源质供应器] 已连接接收器 (" + list.size() + "/" + TileEssentiaProvider.MAX_LINKED_RECEIVERS + "):"));
        if (list.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7  （无）"));
        } else {
            for (BlockPos rp : list) {
                player.sendSystemMessage(Component.literal("§a  [" + rp.getX() + ", " + rp.getY() + ", " + rp.getZ() + "]"));
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

        // ===== Shift+右键：绑定 / 断开（顺序不限，第二步完成绑定） =====
        if (player.isShiftKeyDown()) {
            if (be instanceof TileEssentiaProviderConnection receiver) {
                if (receiver.getLinkedProviderPos() != null) {
                    // 已连接 → 断开
                    receiver.clearLink();
                    player.sendSystemMessage(Component.literal("§e已断开该源质无线接收器的连接"));
                    return InteractionResult.SUCCESS;
                }
                CompoundTag tag = getSelectionTag(stack);
                if (tag.contains(KEY_SELECTED_PROVIDER)) {
                    // 已选中供应器 → 绑定到它
                    BlockPos providerPos = BlockPos.of(tag.getLong(KEY_SELECTED_PROVIDER));
                    String error = receiver.setLinkedProvider(providerPos);
                    if (error == null) {
                        player.sendSystemMessage(Component.literal("§a绑定成功！无线接收器已连接至源质供应器"));
                    } else {
                        player.sendSystemMessage(Component.literal(error));
                    }
                    clearSelection(stack);
                } else if (tag.contains(KEY_SELECTED_RECEIVER)) {
                    player.sendSystemMessage(Component.literal("§c接收器不能互相绑定，只能绑定到源质供应器"));
                } else {
                    // 未选中 → 选中该接收器
                    tag.putLong(KEY_SELECTED_RECEIVER, pos.asLong());
                    setSelectionTag(stack, tag);
                    player.sendSystemMessage(Component.literal("§a已选中源质无线接收器，请 shift+右键 源质供应器完成绑定"));
                }
                return InteractionResult.SUCCESS;
            }
            if (be instanceof TileEssentiaProvider) {
                CompoundTag tag = getSelectionTag(stack);
                if (tag.contains(KEY_SELECTED_RECEIVER)) {
                    // 已选中接收器 → 绑定到本供应器
                    BlockPos receiverPos = BlockPos.of(tag.getLong(KEY_SELECTED_RECEIVER));
                    if (level.getBlockEntity(receiverPos) instanceof TileEssentiaProviderConnection receiver) {
                        String error = receiver.setLinkedProvider(pos);
                        if (error == null) {
                            player.sendSystemMessage(Component.literal("§a绑定成功！无线接收器已连接至源质供应器"));
                        } else {
                            player.sendSystemMessage(Component.literal(error));
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§c选中的源质无线接收器不存在或已被移除"));
                    }
                    clearSelection(stack);
                } else {
                    // 未选中 → 选中该供应器
                    tag.putLong(KEY_SELECTED_PROVIDER, pos.asLong());
                    setSelectionTag(stack, tag);
                    player.sendSystemMessage(Component.literal("§a已选中源质供应器，请 shift+右键 源质无线接收器完成绑定"));
                }
                return InteractionResult.SUCCESS;
            }
            // 其他方块：清除选中
            if (getSelectionTag(stack).contains(KEY_SELECTED_RECEIVER)
                    || getSelectionTag(stack).contains(KEY_SELECTED_PROVIDER)) {
                clearSelection(stack);
                player.sendSystemMessage(Component.literal("§7已清除选中"));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // ===== 右键：显示连接信息（复制显示） =====
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
            player.sendSystemMessage(Component.literal("§7已清除选中"));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
