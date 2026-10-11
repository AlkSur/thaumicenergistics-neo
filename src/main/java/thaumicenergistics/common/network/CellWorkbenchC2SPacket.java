package thaumicenergistics.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.ContainerEssentiaCellWorkbench;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端→服务端 网络包：源质元件工作台分区操作。
 * 操作类型见 ACTION_* 常量；aspectA/aspectB 仅在对应操作使用（其余传 EMPTY）。
 */
public record CellWorkbenchC2SPacket(
        int containerId,
        int action,
        ResourceLocation aspectA,
        ResourceLocation aspectB
) implements CustomPacketPayload {

    public static final int ACTION_REQUEST = 0;
    public static final int ACTION_ADD = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_REPLACE = 3;
    public static final int ACTION_CLEAR = 4;
    public static final int ACTION_PARTITION_TO_CONTENTS = 5;

    private static final ResourceLocation EMPTY = ResourceLocation.withDefaultNamespace("empty");

    public static final Type<CellWorkbenchC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("cell_workbench_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CellWorkbenchC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CellWorkbenchC2SPacket::containerId,
                    ByteBufCodecs.VAR_INT, CellWorkbenchC2SPacket::action,
                    ResourceLocation.STREAM_CODEC, CellWorkbenchC2SPacket::aspectA,
                    ResourceLocation.STREAM_CODEC, CellWorkbenchC2SPacket::aspectB,
                    CellWorkbenchC2SPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ===== 客户端静态发送（从当前打开的工作台菜单自动取 containerId） =====

    private static void send(int action, ResourceLocation a, ResourceLocation b) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PacketDistributor.sendToServer(new CellWorkbenchC2SPacket(player.containerMenu.containerId, action, a, b));
    }

    public static void sendRequestPartitions() {
        send(ACTION_REQUEST, EMPTY, EMPTY);
    }

    public static void sendAddAspect(ResourceLocation aspect) {
        send(ACTION_ADD, aspect, EMPTY);
    }

    public static void sendRemoveAspect(ResourceLocation aspect) {
        send(ACTION_REMOVE, aspect, EMPTY);
    }

    public static void sendReplaceAspect(ResourceLocation from, ResourceLocation to) {
        send(ACTION_REPLACE, from, to);
    }

    public static void sendClear() {
        send(ACTION_CLEAR, EMPTY, EMPTY);
    }

    public static void sendPartitionToContents() {
        send(ACTION_PARTITION_TO_CONTENTS, EMPTY, EMPTY);
    }

    // ===== 服务端处理 =====

    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu.containerId == this.containerId
                    && ctx.player().containerMenu instanceof ContainerEssentiaCellWorkbench menu) {
                boolean changed;
                switch (this.action) {
                    case ACTION_REQUEST -> {
                        if (ctx.player() instanceof ServerPlayer sp) {
                            CellWorkbenchSyncPacket.sendToClient(sp, menu.getPartitionAspects());
                        }
                        return;
                    }
                    case ACTION_ADD -> changed = menu.addAspectToPartition(this.aspectA);
                    case ACTION_REMOVE -> changed = menu.removeAspectFromPartition(this.aspectA);
                    case ACTION_REPLACE -> changed = menu.replaceAspectInPartition(this.aspectA, this.aspectB);
                    case ACTION_CLEAR -> {
                        menu.clearPartitioning();
                        changed = true;
                    }
                    case ACTION_PARTITION_TO_CONTENTS -> {
                        menu.partitionToContents();
                        changed = true;
                    }
                    default -> changed = false;
                }
                if (changed) {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        CellWorkbenchSyncPacket.sendToClient(sp, menu.getPartitionAspects());
                    }
                }
            }
        });
    }
}
