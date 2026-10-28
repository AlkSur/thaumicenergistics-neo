package thaumicenergistics.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.common.container.EssentiaFillHelper;
import thaumicenergistics.common.container.MenuResources;

/**
 * 客户端→服务端：源质容器存入 ME 网络。
 * <ul>
 *   <li>手持（carried/主手）源质容器右键 → 源质存入，空容器放回玩家背包（slotIndex=-1）。</li>
 *   <li>Shift+右键玩家背包格 → 源质存入，空容器留在原背包格（slotIndex>=0）。</li>
 * </ul>
 */
public record EssentiaTerminalDepositC2SPacket(int containerId, int slotIndex,
        ItemStack sourceStack) implements CustomPacketPayload {

    public static final Type<EssentiaTerminalDepositC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("essentia_terminal_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaTerminalDepositC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EssentiaTerminalDepositC2SPacket::containerId,
                    ByteBufCodecs.VAR_INT, EssentiaTerminalDepositC2SPacket::slotIndex,
                    ItemStack.OPTIONAL_STREAM_CODEC, EssentiaTerminalDepositC2SPacket::sourceStack,
                    EssentiaTerminalDepositC2SPacket::new
            );

    /** 手持源质物品存入（客户端可能持有在 carried 或主手）。 */
    public static EssentiaTerminalDepositC2SPacket fromClientStack(int containerId, ItemStack stack) {
        return new EssentiaTerminalDepositC2SPacket(containerId, -1, stack.copy());
    }

    /** 从玩家背包格存入（Shift+右键背包格）。 */
    public static EssentiaTerminalDepositC2SPacket fromSlot(int containerId, int slotIndex, ItemStack stack) {
        return new EssentiaTerminalDepositC2SPacket(containerId, slotIndex, stack.copy());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 任意 ME 终端菜单都支持：源质容器右键 → 源质存入网络
            if (ctx.player().containerMenu.containerId == this.containerId
                    && ctx.player().containerMenu instanceof MEStorageMenu menu
                    && menu.getLinkStatus().connected()) {
                var player = menu.getPlayerInventory().player;
                if (slotIndex < -1 || slotIndex >= player.getInventory().items.size()) {
                    return;
                }
                if (slotIndex >= 0 && menu.slots.stream().noneMatch(slot ->
                        menu.isPlayerSideSlot(slot) && slot.getSlotIndex() == slotIndex)) {
                    return;
                }
                boolean carried = slotIndex < 0 && !menu.getCarried().isEmpty();
                var source = slotIndex >= 0 ? player.getInventory().getItem(slotIndex)
                        : carried ? menu.getCarried() : player.getMainHandItem();
                // 客户端副本仅用于拒绝过时操作，不能作为源质来源。
                if (!ItemStack.isSameItemSameComponents(source, sourceStack)
                        || source.getCount() != sourceStack.getCount()) {
                    menu.broadcastChanges();
                    return;
                }
                if (!source.isEmpty()) {
                    var result = EssentiaFillHelper.depositContainerContent(
                            MenuResources.getStorage(menu),
                            MenuResources.getEnergySource(menu),
                            menu.getActionSource(), player, source);
                    if (result != null) {
                        if (this.slotIndex >= 0) {
                            // Shift+右键背包格：空容器留在原背包格
                            player.getInventory().setItem(this.slotIndex, result);
                        } else {
                            // 更新实际来源；部分成功时留下包含余量的容器。
                            if (carried) {
                                menu.setCarried(result);
                            } else {
                                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, result);
                            }
                        }
                    }
                }
                menu.broadcastChanges();
            }
        });
    }
}
