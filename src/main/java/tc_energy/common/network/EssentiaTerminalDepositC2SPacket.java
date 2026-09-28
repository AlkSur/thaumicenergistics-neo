package tc_energy.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tc_energy.ThaumicEnergistics;

/**
 * 客户端→服务端：源质容器存入 ME 网络。
 * <ul>
 *   <li>手持（carried/主手）源质容器右键 → 源质存入，空容器放回玩家背包（slotIndex=-1）。</li>
 *   <li>Shift+右键玩家背包格 → 源质存入，空容器留在原背包格（slotIndex>=0）。</li>
 * </ul>
 */
public record EssentiaTerminalDepositC2SPacket(int containerId, int slotIndex,
        net.minecraft.world.item.ItemStack sourceStack) implements CustomPacketPayload {

    public static final Type<EssentiaTerminalDepositC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("essentia_terminal_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaTerminalDepositC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EssentiaTerminalDepositC2SPacket::containerId,
                    ByteBufCodecs.VAR_INT, EssentiaTerminalDepositC2SPacket::slotIndex,
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC, EssentiaTerminalDepositC2SPacket::sourceStack,
                    EssentiaTerminalDepositC2SPacket::new
            );

    /** 手持源质物品存入（客户端可能持有在 carried 或主手）。 */
    public static EssentiaTerminalDepositC2SPacket fromClientStack(int containerId, net.minecraft.world.item.ItemStack stack) {
        return new EssentiaTerminalDepositC2SPacket(containerId, -1, stack.copy());
    }

    /** 从玩家背包格存入（Shift+右键背包格）。 */
    public static EssentiaTerminalDepositC2SPacket fromSlot(int containerId, int slotIndex, net.minecraft.world.item.ItemStack stack) {
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
                    && ctx.player().containerMenu instanceof appeng.menu.me.common.MEStorageMenu menu
                    ) {
                var player = menu.getPlayerInventory().player;
                // 源质物品：优先包携带的 sourceStack（客户端真实来源）；否则回退菜单 carried/主手
                var source = !this.sourceStack.isEmpty() ? this.sourceStack
                        : (!menu.getCarried().isEmpty() ? menu.getCarried()
                           : player.getMainHandItem());
                if (!source.isEmpty()) {
                    var result = tc_energy.common.container.EssentiaFillHelper.extractContainerContent(
                            tc_energy.common.container.MenuAccess.getStorage(menu), tc_energy.common.container.MenuAccess.getEnergySource(menu),
                            menu.getActionSource(), source);
                    if (result != null) {
                        if (this.slotIndex >= 0) {
                            // Shift+右键背包格：空容器留在原背包格
                            player.getInventory().setItem(this.slotIndex, result);
                        } else {
                            // 手持：清空 carried，空容器放回玩家背包（不留 carried，防被 AE2 自动放入网络）
                            menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                            if (!result.isEmpty()) {
                                if (!player.getInventory().add(result)) {
                                    player.drop(result, false);
                                }
                            }
                        }
                    }
                }
                menu.broadcastChanges();
            }
        });
    }
}
