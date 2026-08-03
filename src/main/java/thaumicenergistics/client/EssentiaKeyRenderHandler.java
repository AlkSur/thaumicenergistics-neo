package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.gui.AspectGuiRenderer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

import java.util.ArrayList;
import java.util.List;

public class EssentiaKeyRenderHandler implements appeng.api.client.AEKeyRenderHandler<AEssentiaKey> {

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
    }

    @Override
    public Component getDisplayName(AEssentiaKey stack) {
        return stack.getDisplayName(); // 由 AEssentiaKey.computeDisplayName() 返回源质真实名称
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey stack) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getDisplayName());
        tooltip.add(Component.literal(appeng.util.Platform.formatModName(stack.getModId())));
        return tooltip;
    }
}