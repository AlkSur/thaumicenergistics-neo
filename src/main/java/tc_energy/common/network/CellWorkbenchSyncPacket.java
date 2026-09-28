package tc_energy.common.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.container.GuiEssentiaCellWorkbench;

/**
 * 服务端→客户端 网络包：同步源质元件工作台的分区列表。
 *
 * 客户端收到后更新 {@link GuiEssentiaCellWorkbench#updatePartitions}，刷新 63 个分区格。
 */
public record CellWorkbenchSyncPacket(
        List<ResourceLocation> partitions
) implements CustomPacketPayload {

    public static final Type<CellWorkbenchSyncPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("cell_workbench_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CellWorkbenchSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.partitions.size());
                for (ResourceLocation rl : pkt.partitions) {
                    ResourceLocation.STREAM_CODEC.encode(buf, rl);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<ResourceLocation> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(ResourceLocation.STREAM_CODEC.decode(buf));
                }
                return new CellWorkbenchSyncPacket(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端发送分区列表到指定玩家。 */
    public static void sendToClient(ServerPlayer player, List<ResourceLocation> partitions) {
        PacketDistributor.sendToPlayer(player, new CellWorkbenchSyncPacket(partitions));
    }

    /** 客户端处理：更新打开的工作台 GUI 分区格。 */
    public void handleOnClient(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof GuiEssentiaCellWorkbench gui) {
                gui.updatePartitions(this.partitions);
            }
        });
    }
}
