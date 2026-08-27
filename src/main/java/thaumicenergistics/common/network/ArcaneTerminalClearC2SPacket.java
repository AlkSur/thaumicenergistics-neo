package thaumicenergistics.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;

/**
 * 客户端→服务端 网络包：奥术合成终端清空合成网格 / 上报"关闭时自动清理"开关。
 * <ul>
 *   <li>{@link #ACTION_STASH}：立即把网格物品存入 ME 网络（S_STASH 按钮 / 关闭终端时触发），
 *       服务端直接执行，不依赖 AE2 quickMove/MOVE_REGION 语义链。</li>
 *   <li>{@link #ACTION_SET_CLEAR_ON_CLOSE}：上报客户端 AE2 设置"关闭时自动清理合成槽"的开关值，
 *       服务端存到菜单实例上，菜单关闭（removed）时据此清理——不依赖 GUI onClose 是否触发。</li>
 * </ul>
 */
public record ArcaneTerminalClearC2SPacket(int containerId, int action, boolean flag) implements CustomPacketPayload {

    public static final int ACTION_STASH = 0;
    public static final int ACTION_SET_CLEAR_ON_CLOSE = 1;

    public static final Type<ArcaneTerminalClearC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("arcane_terminal_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneTerminalClearC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ArcaneTerminalClearC2SPacket::containerId,
                    ByteBufCodecs.VAR_INT, ArcaneTerminalClearC2SPacket::action,
                    ByteBufCodecs.BOOL, ArcaneTerminalClearC2SPacket::flag,
                    ArcaneTerminalClearC2SPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu.containerId == this.containerId
                    && ctx.player().containerMenu instanceof ContainerArcaneCraftingTerminal menu) {
                switch (this.action) {
                    case ACTION_STASH -> menu.clearGridToNetworkOnServer();
                    case ACTION_SET_CLEAR_ON_CLOSE -> menu.setClearOnClose(this.flag);
                    default -> {
                    }
                }
            }
        });
    }
}
