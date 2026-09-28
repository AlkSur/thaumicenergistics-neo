package tc_energy.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.entities.BackpackSkins;
import dev.tc4port.thaumcraft.entity.ThaumcraftGolemEntity;

/**
 * 服务端→客户端 网络包：同步傀儡背包的连接状态和皮肤。
 *
 * 替代 1.7.10 的 DataWatcher 同步机制（SYNCFLAG_HAS_WIFI_IN_RANGE / OUT_OF_RANGE / NO_WIFI）。
 *
 * 服务端每 18 tick 检测一次背包连接状态，变化时发送此包到所有追踪该傀儡的客户端。
 * 客户端收到后更新 {@link GolemBackpackClientData}，供渲染层读取。
 */
public record GolemBackpackSyncPacket(
        /** 傀儡实体 ID */
        int entityId,
        /** 连接状态：0=无背包, 1=在范围内, 2=超出范围 */
        int connectionStatus,
        /** 皮肤序号（对应 {@link BackpackSkins} ordinal） */
        int skinOrdinal
) implements CustomPacketPayload {

    public static final Type<GolemBackpackSyncPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("golem_backpack_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GolemBackpackSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.entityId);
                buf.writeVarInt(pkt.connectionStatus);
                buf.writeVarInt(pkt.skinOrdinal);
            },
            buf -> new GolemBackpackSyncPacket(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 连接状态常量 */
    public static final int STATUS_NO_BACKPACK = 0;
    public static final int STATUS_IN_RANGE = 1;
    public static final int STATUS_OUT_OF_RANGE = 2;

    /**
     * 客户端处理：更新该傀儡的客户端背包数据缓存。
     */
    public void handleOnClient(IPayloadContext ctx) {
        // 在主线程上执行
        ctx.enqueueWork(() -> {
            // 客户端世界在切换维度/退出存档的瞬间可能为 null，直接解引用会 NPE
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Entity entity = level.getEntity(this.entityId);
            if (entity instanceof ThaumcraftGolemEntity golem) {
                GolemBackpackClientData.update(golem, this.connectionStatus, this.skinOrdinal);
            }
        });
    }
}
