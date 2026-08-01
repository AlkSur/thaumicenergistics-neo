package thaumicenergistics.client.render.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 傀儡无线背包模型（简化版）。
 *
 * 包含三个部件：Antenna（天线，1×3×1）、PackBack（背包主体，2×6×6）、PackFront（背包前面板，1×2×4）。
 * 旋转的福鲁伊克斯珍珠由 GolemBackpackLayer 在渲染时手动绘制。
 * 对应 1.7.10 的 ModelGolemWifiBackpack，简化为统一的 Thaumium 皮肤。
 */
public class GolemBackpackModel {

    private final ModelPart root;

    public GolemBackpackModel(ModelPart root) {
        this.root = root;
    }

    /**
     * 创建 LayerDefinition，供 {@code EntityRenderersEvent.RegisterLayerDefinitions} 注册。
     * 纹理尺寸 16×16，与 1.7.10 一致。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();

        // 天线：1×3×1，纹理偏移 (10,0)，X 轴旋转 180°
        part.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(10, 0)
                        .addBox(-0.5f, -6f, -0.5f, 1, 3, 1),
                PartPose.rotation((float) Math.PI, 0, 0));

        // 背包主体：2×6×6，纹理偏移 (0,0)，X 轴旋转 180°
        part.addOrReplaceChild("pack_back",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1f, -3f, -3f, 2, 6, 6),
                PartPose.rotation((float) Math.PI, 0, 0));

        // 背包前面板：1×2×4，纹理偏移 (2,0)，X 轴旋转 180°
        part.addOrReplaceChild("pack_front",
                CubeListBuilder.create().texOffs(2, 0)
                        .addBox(-1.5f, -1f, -2f, 1, 2, 4),
                PartPose.rotation((float) Math.PI, 0, 0));

        return LayerDefinition.create(mesh, 16, 16);
    }

    /**
     * 渲染背包箱体（天线 + 主体 + 前面板）。
     */
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                                int light, int overlay, float pearlRotation) {
        root.render(poseStack, buffer, light, overlay);
    }

    /**
     * 渲染旋转的福鲁伊克斯珍珠：位于天线上方，4 个面围绕天线绘制，随 pearlRotation 旋转。
     * UV 坐标：minU=0.5, maxU=0.84375, minV=0.375, maxV=0.71875（16×16 纹理）。
     *
     * @param poseStack PoseStack
     * @param buffer    VertexConsumer
     * @param light     packed light
     * @param overlay   packed overlay
     * @param pearlRotation 旋转角度（度）
     * @param inRange   true=正常紫色，false=红色（不在范围内）
     */
    public void renderPearl(PoseStack poseStack, VertexConsumer buffer,
                            int light, int overlay, float pearlRotation, boolean inRange) {
        float pearlScale = 0.2f;
        float antennaWidth = 0.32f;
        float faceDist = -0.013f / pearlScale;
        float twoFD = faceDist + faceDist;

        // UV 坐标（16×16 纹理）
        float minU = 8.0f / 16.0f;
        float maxU = 13.5f / 16.0f;
        float minV = 6.0f / 16.0f;
        float maxV = 11.5f / 16.0f;

        // 颜色：不在范围内时红色
        int red = inRange ? 255 : 255;
        int green = inRange ? 255 : 0;
        int blue = inRange ? 255 : 0;

        poseStack.pushPose();

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(pearlRotation));

        poseStack.scale(pearlScale, pearlScale, pearlScale);

        // 左面
        poseStack.pushPose();
        poseStack.translate(-0.0955f, 0.275f, -0.045f);
        drawPearlFace(poseStack, buffer, light, overlay, minU, maxU, minV, maxV, red, green, blue, 0, 0, 1);
        // 右面
        poseStack.translate(0, 0, antennaWidth - twoFD);
        drawPearlFace(poseStack, buffer, light, overlay, maxU, minU, minV, maxV, red, green, blue, 0, 0, -1);
        poseStack.popPose();

        // 前后两面（旋转 90°）
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        poseStack.translate(-0.0955f, 0.275f, -0.045f - antennaWidth - faceDist);
        drawPearlFace(poseStack, buffer, light, overlay, maxU, minU, minV, maxV, red, green, blue, 0, 0, -1);
        poseStack.translate(0, 0, antennaWidth - twoFD);
        drawPearlFace(poseStack, buffer, light, overlay, minU, maxU, minV, maxV, red, green, blue, 0, 0, 1);
        poseStack.popPose();

        poseStack.popPose();
    }

    /**
     * 绘制珍珠的一个面（正反两面，共 8 个顶点），手动通过矩阵变换顶点坐标。
     * 使用 1.21.1 的 VertexConsumer 新 API：addVertex + setColor + setUv + setOverlay + setLight + setNormal。
     */
    private void drawPearlFace(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                               float minU, float maxU, float minV, float maxV,
                               int r, int g, int b, float nx, float ny, float nz) {
        Matrix4f matrix = poseStack.last().pose();

        // 四个角点的本地坐标
        float[][] corners = {
                {0, 0, 0},  // 左下
                {1, 0, 0},  // 右下
                {1, 1, 0},  // 右上
                {0, 1, 0}   // 左上
        };

        // 正面 UV
        float[][] frontUvs = {
                {maxU, maxV},
                {minU, maxV},
                {minU, minV},
                {maxU, minV}
        };

        // 背面 UV（镜像）
        float[][] backUvs = {
                {maxU, minV},
                {minU, minV},
                {minU, maxV},
                {maxU, maxV}
        };

        Vector4f transformed = new Vector4f();

        // 正面
        for (int i = 0; i < 4; i++) {
            transformed.set(corners[i][0], corners[i][1], corners[i][2], 1.0f);
            matrix.transform(transformed);
            buffer.addVertex(transformed.x, transformed.y, transformed.z)
                    .setColor(r, g, b, 255)
                    .setUv(frontUvs[i][0], frontUvs[i][1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(nx, ny, nz);
        }

        // 背面（逆序 + 翻转法线）
        for (int i = 0; i < 4; i++) {
            transformed.set(corners[i][0], corners[i][1], corners[i][2], 1.0f);
            matrix.transform(transformed);
            buffer.addVertex(transformed.x, transformed.y, transformed.z)
                    .setColor(r, g, b, 255)
                    .setUv(backUvs[i][0], backUvs[i][1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(-nx, -ny, -nz);
        }
    }
}
