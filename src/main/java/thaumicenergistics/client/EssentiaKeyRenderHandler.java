package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.gui.AspectGuiRenderer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

import java.util.ArrayList;
import java.util.List;
import appeng.api.client.AEKeyRenderHandler;
import appeng.util.Platform;

public class EssentiaKeyRenderHandler implements AEKeyRenderHandler<AEssentiaKey> {

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AEssentiaKey stack) {
        // 复用 TC 的 AspectGuiRenderer（positionTexColor shader + enableBlend）——透明背景正确，
        // 与蒸馏编码台一致；避免 GuiGraphics.blit 的 blend 状态问题（图标内部镂空被填黑）。
        String tag = stack.getId().getPath();
        Aspect aspect = Aspect.getAspect(tag);
        if (aspect != null) {
            AspectGuiRenderer.draw(guiGraphics, aspect, x, y, 16, 1.0f);
        }
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, AEssentiaKey what,
                                float scale, int combinedLight, Level level) {
        Aspect aspect = Aspect.getAspect(what.getId().getPath());
        if (aspect == null || scale <= 0) return;
        int color = 0xFF000000 | aspect.getColor();
        float half = scale / 2;
        poseStack.pushPose();
        try {
            // Direct aspect textures are not sprites in the block texture atlas.
            poseStack.translate(0, 0, 0.01f);
            var pose = poseStack.last();
            var buffer = buffers.getBuffer(RenderType.entityTranslucent(aspect.getImage()));
            var normal = new org.joml.Vector3f(0, 0, 1);
            pose.normal().transform(normal).normalize();
            buffer.addVertex(pose, -half, -half, 0).setColor(color).setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
                    .setNormal(normal.x, normal.y, normal.z);
            buffer.addVertex(pose, half, -half, 0).setColor(color).setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
                    .setNormal(normal.x, normal.y, normal.z);
            buffer.addVertex(pose, half, half, 0).setColor(color).setUv(1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
                    .setNormal(normal.x, normal.y, normal.z);
            buffer.addVertex(pose, -half, half, 0).setColor(color).setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
                    .setNormal(normal.x, normal.y, normal.z);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public Component getDisplayName(AEssentiaKey stack) {
        return stack.getDisplayName(); // 由 AEssentiaKey.computeDisplayName() 返回源质真实名称
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey stack) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getDisplayName());
        tooltip.add(Component.literal(Platform.formatModName(stack.getModId())));
        return tooltip;
    }
}