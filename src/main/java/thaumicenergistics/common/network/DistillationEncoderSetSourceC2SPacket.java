package thaumicenergistics.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.ContainerDistillationPatternEncoder;

/**
 * 客户端→服务端 网络包：蒸馏编码台设置源物品（JEI 拖拽）。
 * 源物品槽是真实 SlotItemHandler（非 ghost），拖拽需服务端权威写入 encoder 槽 0 + 刷新 aspects。
 */
public record DistillationEncoderSetSourceC2SPacket(
        int containerId,
        ItemStack stack
) implements CustomPacketPayload {

    public static final Type<DistillationEncoderSetSourceC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("de_set_source"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DistillationEncoderSetSourceC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DistillationEncoderSetSourceC2SPacket::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC, DistillationEncoderSetSourceC2SPacket::stack,
                    DistillationEncoderSetSourceC2SPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理：找到对应菜单，写入源物品槽并刷新 aspects。 */
    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu.containerId == this.containerId
                    && ctx.player().containerMenu instanceof ContainerDistillationPatternEncoder menu) {
                menu.setSourceItemOnServer(this.stack);
            }
        });
    }
}
