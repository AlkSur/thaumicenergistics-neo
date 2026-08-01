package thaumicenergistics.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.ContainerKnowledgeInscriber;

/**
 * 客户端→服务端 网络包：同步知识记录仪合成网格中某个幽灵槽的内容。
 * 幽灵槽不走标准 Slot 同步流程（因为不消耗物品，只是摆个样子），
 * 服务端需要知道客户端摆了什么才能计算奥术配方、更新保存按钮状态。
 */
public record KnowledgeInscriberGhostSlotPacket(
        /** Container 的 containerId，用来在服务端找到对应菜单实例 */
        int containerId,
        /** 幽灵槽在合成网格中的索引（0-8） */
        int slotIndex,
        /** 该槽要设置的物品 */
        ItemStack stack
) implements CustomPacketPayload {

    public static final Type<KnowledgeInscriberGhostSlotPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("ki_ghost_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeInscriberGhostSlotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, KnowledgeInscriberGhostSlotPacket::containerId,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, KnowledgeInscriberGhostSlotPacket::slotIndex,
                    ItemStack.OPTIONAL_STREAM_CODEC, KnowledgeInscriberGhostSlotPacket::stack,
                    KnowledgeInscriberGhostSlotPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：找到对应菜单，更新幽灵槽，重新计算配方。
     */
    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu.containerId == this.containerId
                    && ctx.player().containerMenu instanceof ContainerKnowledgeInscriber menu) {
                menu.setGhostSlotOnServer(this.slotIndex, this.stack);
            }
        });
    }
}
