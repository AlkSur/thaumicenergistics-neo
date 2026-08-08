package thaumicenergistics.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.ResearchGateOverlay;

/**
 * 服务端→客户端 网络包：告知玩家"研究未解锁"拦截发生，并携带具体研究键。
 *
 * 服务端在 submitJob 拦截（MixinCraftingService）时发送到对应玩家；
 * 客户端收到后由 {@link ResearchGateOverlay} 在最上层渲染提示。
 */
public record ResearchGateErrorPacket(String research) implements CustomPacketPayload {

    public static final Type<ResearchGateErrorPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("research_gate_error"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchGateErrorPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeUtf(pkt.research),
            buf -> new ResearchGateErrorPacket(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：记录最近一次研究未解锁拦截的研究键，供渲染层读取。
     */
    public void handleOnClient(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 无论当前在什么界面，记录研究键
            ResearchGateOverlay.setPendingResearch(this.research);
        });
    }
}
