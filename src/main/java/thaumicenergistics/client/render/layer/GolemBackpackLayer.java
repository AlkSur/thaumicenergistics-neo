package thaumicenergistics.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import thaumcraft.client.renderers.entity.LegacyGolemModel;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumicenergistics.client.render.model.GolemBackpackModel;
import thaumicenergistics.common.entities.BackpackSkins;
import thaumicenergistics.common.network.GolemBackpackClientData;
import thaumicenergistics.common.network.GolemBackpackSyncPacket;

/**
 * 傀儡无线背包渲染层。
 * 当傀儡装备了无线背包时，在背上渲染天线 + 背包盒（使用当前皮肤纹理），以及旋转的福鲁伊克斯珍珠（绿色=连接，红色=断开）。
 * 数据来源：{@link GolemBackpackClientData}（由 S2C 网络包更新）。
 * 皮肤和连接状态均从客户端缓存读取，不直接查询 NBT。
 */
@OnlyIn(Dist.CLIENT)
public class GolemBackpackLayer extends RenderLayer<ThaumcraftGolemEntity, LegacyGolemModel> {

    /** 默认纹理（Thaumium 皮肤） */
    private static final ResourceLocation DEFAULT_TEXTURE =
            BackpackSkins.Thaumium.getTextureLocation();

    /** 珍珠旋转速度（度/tick） */
    private static final float PEARL_ROTATION_SPEED = 2.0f;

    private final GolemBackpackModel model;

    public GolemBackpackLayer(RenderLayerParent<ThaumcraftGolemEntity, LegacyGolemModel> parent,
                              GolemBackpackModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       ThaumcraftGolemEntity golem, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 仅在傀儡装备了背包时渲染（从客户端缓存读取）
        if (!GolemBackpackClientData.hasBackpack(golem)) {
            return;
        }

        // 从客户端缓存读取连接状态和皮肤
        boolean inRange = GolemBackpackClientData.isInRange(golem);
        BackpackSkins skin = GolemBackpackClientData.getSkin(golem);
        ResourceLocation texture = skin.getTextureLocation();

        float pearlRotation = (golem.tickCount + partialTick) * PEARL_ROTATION_SPEED;

        VertexConsumer buffer = bufferSource.getBuffer(cutoutRenderType(texture));

        // 对齐到傀儡身体
        poseStack.pushPose();
        // 傀儡模型尺寸约 0.4×0.95，背包放在背部偏上
        poseStack.scale(0.5f, 0.5f, 0.5f);
        // 略微后移到背上
        poseStack.translate(0, 0.5, -1.5);

        model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, pearlRotation);

        // 渲染珍珠（使用 entityTranslucent 允许半透明效果）
        VertexConsumer pearlBuffer = bufferSource.getBuffer(translucentRenderType(texture));
        model.renderPearl(poseStack, pearlBuffer, packedLight, OverlayTexture.NO_OVERLAY,
                pearlRotation, inRange);

        poseStack.popPose();
    }

    /** 按贴图缓存 RenderType，避免每帧创建（渲染主线程单线程安全）。 */
    private static final java.util.Map<ResourceLocation, RenderType> CUTOUT_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, RenderType> TRANSLUCENT_CACHE = new java.util.HashMap<>();

    private static RenderType cutoutRenderType(ResourceLocation tex) {
        return CUTOUT_CACHE.computeIfAbsent(tex, RenderType::entityCutoutNoCull);
    }

    private static RenderType translucentRenderType(ResourceLocation tex) {
        return TRANSLUCENT_CACHE.computeIfAbsent(tex, RenderType::entityTranslucent);
    }
}
