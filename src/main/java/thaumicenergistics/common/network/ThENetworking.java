package thaumicenergistics.common.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ThENetworking {

    private ThENetworking() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ThENetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("thaumicenergistics").versioned("1");

        // GolemBackpackSyncPacket — S2C（服务端→客户端，同步背包连接状态和皮肤）
        registrar.playToClient(
                GolemBackpackSyncPacket.TYPE,
                GolemBackpackSyncPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> pkt.handleOnClient(ctx))
        );

        // KnowledgeInscriberGhostSlotPacket — C2S（客户端→服务端，同步知识记录仪幽灵槽）
        registrar.playToServer(
                KnowledgeInscriberGhostSlotPacket.TYPE,
                KnowledgeInscriberGhostSlotPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // DistillationEncoderSetSourceC2SPacket — C2S（蒸馏编码台设置源物品——JEI 拖拽）
        registrar.playToServer(
                DistillationEncoderSetSourceC2SPacket.TYPE,
                DistillationEncoderSetSourceC2SPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // CellWorkbenchC2SPacket — C2S（客户端→服务端，源质元件工作台分区操作）
        registrar.playToServer(
                CellWorkbenchC2SPacket.TYPE,
                CellWorkbenchC2SPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // CellWorkbenchSyncPacket — S2C（服务端→客户端，同步工作台分区列表）
        registrar.playToClient(
                CellWorkbenchSyncPacket.TYPE,
                CellWorkbenchSyncPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnClient(ctx)
        );
    }
}
