package tc_energy.common.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ThENetworking {

    private ThENetworking() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ThENetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("tc_energy").versioned("1");

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

        // EssentiaTerminalFillC2SPacket — C2S（客户端→服务端，源质终端右键装罐）
        registrar.playToServer(
                EssentiaTerminalFillC2SPacket.TYPE,
                EssentiaTerminalFillC2SPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // EssentiaTerminalDepositC2SPacket — C2S（客户端→服务端，手持源质容器右键存入网络）
        registrar.playToServer(
                EssentiaTerminalDepositC2SPacket.TYPE,
                EssentiaTerminalDepositC2SPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // EssentiaContainerInteractC2SPacket — C2S（源质容器整组存入/装填；替代旧 MixinMEStorageScreen 整组语义）
        registrar.playToServer(
                EssentiaContainerInteractC2SPacket.TYPE,
                EssentiaContainerInteractC2SPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // ArcaneTerminalClearC2SPacket — C2S（奥术合成终端清空合成网格——S_STASH 按钮 / 关闭时自动清理）
        registrar.playToServer(
                ArcaneTerminalClearC2SPacket.TYPE,
                ArcaneTerminalClearC2SPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnServer(ctx)
        );

        // AssemblerPreviewSyncPacket — S2C（奥术装配器配方预览：3×3 材料 + 预产物）
        registrar.playToClient(
                AssemblerPreviewSyncPacket.TYPE,
                AssemblerPreviewSyncPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handleOnClient(ctx)
        );
    }
}
