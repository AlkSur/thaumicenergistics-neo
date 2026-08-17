package thaumicenergistics.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;

/**
 * 客户端→服务端：源质终端右键源质条目装罐。
 * 客户端已把 serial 解析为 aspect id（服务端无 client repo，无法解析 serial）。
 * 携带 sourceStack：罐子可能持于主手（未进入 carried），服务端直接用客户端栈，
 * 避免只读 menu.getCarried() 为空导致装罐静默失败。
 */
public record EssentiaTerminalFillC2SPacket(int containerId, ResourceLocation aspectId,
        ItemStack sourceStack) implements CustomPacketPayload {

    public static final Type<EssentiaTerminalFillC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("essentia_terminal_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaTerminalFillC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EssentiaTerminalFillC2SPacket::containerId,
                    ResourceLocation.STREAM_CODEC, EssentiaTerminalFillC2SPacket::aspectId,
                    ItemStack.OPTIONAL_STREAM_CODEC, EssentiaTerminalFillC2SPacket::sourceStack,
                    EssentiaTerminalFillC2SPacket::new
            );

    /** 装罐：sourceStack 为客户端解析出的源质容器（优先 carried，为空回退主手）。 */
    public static EssentiaTerminalFillC2SPacket create(int containerId, ResourceLocation aspectId, ItemStack stack) {
        return new EssentiaTerminalFillC2SPacket(containerId, aspectId, stack.copy());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 通用化：任意 ME 终端菜单都支持右键源质条目装罐（源质终端/奥术合成终端/AE2 ME 终端等）
            if (ctx.player().containerMenu.containerId == this.containerId
                    && ctx.player().containerMenu instanceof appeng.menu.me.common.MEStorageMenu menu
                    && menu instanceof thaumicenergistics.common.container.ThEEssentiaMenuAccess acc) {
                var player = menu.getPlayerInventory().player;
                // 源质物品：优先包携带的 sourceStack（客户端真实来源）；否则回退菜单 carried/主手
                var source = !this.sourceStack.isEmpty() ? this.sourceStack
                        : (!menu.getCarried().isEmpty() ? menu.getCarried()
                           : player.getMainHandItem());
                thaumicenergistics.common.container.EssentiaFillHelper.fillContainerFromGrid(
                        acc.thE_getStorage(), acc.thE_getEnergySource(),
                        menu.getActionSource(), player, source, this.aspectId);
                menu.broadcastChanges();
            }
        });
    }
}
