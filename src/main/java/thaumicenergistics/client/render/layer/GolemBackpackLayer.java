package thaumicenergistics.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import thaumcraft.client.renderers.entity.LegacyGolemModel;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumicenergistics.client.render.model.GolemBackpackModels;
import thaumicenergistics.common.network.GolemBackpackClientData;
import thaumicenergistics.mixins.tc.AccessorLegacyGolemModel;

/** CE backpack geometry attached to the animated Minecraft 1.21.1 golem body. */
@OnlyIn(Dist.CLIENT)
public final class GolemBackpackLayer extends RenderLayer<ThaumcraftGolemEntity, LegacyGolemModel> {
    private final ModelPart root;
    private final ModelPart body;

    public GolemBackpackLayer(RenderLayerParent<ThaumcraftGolemEntity, LegacyGolemModel> parent) {
        super(parent);
        root = ((AccessorLegacyGolemModel) (Object) getParentModel()).the$getRoot();
        body = root.getChild("body");
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int packedLight,
            ThaumcraftGolemEntity golem, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (golem.isInvisible() || !GolemBackpackClientData.hasBackpack(golem)) {
            return;
        }
        poses.pushPose();
        try {
            // LegacyGolemModel applies this scale only inside its own renderToBuffer.
            // Layers must repeat it before applying the animated body hierarchy.
            poses.scale(0.4F, 0.4F, 0.4F);
            root.translateAndRotate(poses);
            body.translateAndRotate(poses);
            GolemBackpackModels.current().render(poses, buffers,
                    GolemBackpackClientData.getSkin(golem),
                    GolemBackpackClientData.isInRange(golem),
                    packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            poses.popPose();
        }
    }
}
