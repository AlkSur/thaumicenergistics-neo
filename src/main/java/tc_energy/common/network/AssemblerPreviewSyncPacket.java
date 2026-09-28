package tc_energy.common.network;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.container.ContainerArcaneAssembler;
import tc_energy.common.tiles.TileArcaneAssembler;

/**
 * S2C：奥术装配器配方预览同步（3×3 材料 + 预产物）。
 * 合成开始（active=true）发送材料与产物，结束/清空（active=false）。
 * 直接发给打开该装配器菜单的玩家，不依赖 BE update tag（与容器数据同步同级可靠）。
 */
public record AssemblerPreviewSyncPacket(
        BlockPos pos,
        List<ItemStack> grid,
        ItemStack result,
        boolean active
) implements CustomPacketPayload {

    public static final Type<AssemblerPreviewSyncPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("assembler_preview_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerPreviewSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AssemblerPreviewSyncPacket::pos,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)), AssemblerPreviewSyncPacket::grid,
                    ItemStack.OPTIONAL_STREAM_CODEC, AssemblerPreviewSyncPacket::result,
                    ByteBufCodecs.BOOL, AssemblerPreviewSyncPacket::active,
                    AssemblerPreviewSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleOnClient(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player != null && player.containerMenu instanceof ContainerArcaneAssembler menu
                    && menu.assembler != null && menu.assembler.getBlockPos().equals(this.pos)) {
                menu.applyClientPreview(this.grid, this.result, this.active);
            }
        });
    }

    /** 发给打开该装配器菜单的所有玩家。 */
    public static void send(TileArcaneAssembler assembler, boolean active) {
        if (assembler == null || assembler.getLevel() == null
                || !(assembler.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        // null → EMPTY 兜底：previewGrid 任何时刻都不应含 null（序列化 ItemStack 会 NPE）
        ItemStack[] grid = assembler.getPreviewGrid();
        List<ItemStack> gridList = new java.util.ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            gridList.add(grid != null && grid[i] != null ? grid[i] : ItemStack.EMPTY);
        }
        ItemStack result = assembler.getCurrentResult();
        var packet = new AssemblerPreviewSyncPacket(
                assembler.getBlockPos(),
                gridList,
                result != null ? result : ItemStack.EMPTY,
                active);
        for (var player : serverLevel.players()) {
            if (player.containerMenu instanceof ContainerArcaneAssembler menu && menu.assembler == assembler) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}
