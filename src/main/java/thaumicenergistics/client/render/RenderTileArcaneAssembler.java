package thaumicenergistics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import thaumicenergistics.common.tiles.TileArcaneAssembler;

/** 奥术装配器的渲染器——显示合成进度动画。 */
public class RenderTileArcaneAssembler implements BlockEntityRenderer<TileArcaneAssembler> {

    public RenderTileArcaneAssembler(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TileArcaneAssembler tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // TODO: Render crafting progress bar and speed upgrade indicators
    }
}
