package thaumicenergistics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;

/** 源质谐振仓的渲染器——显示燃烧进度。 */
public class RenderTileEVC implements BlockEntityRenderer<TileEssentiaVibrationChamber> {

    public RenderTileEVC(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TileEssentiaVibrationChamber tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // TODO: Render flame/burn animation proportional to burnProgress
    }
}
