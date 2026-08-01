package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

import java.util.ArrayList;
import java.util.List;

public class EssentiaKeyRenderHandler implements appeng.api.client.AEKeyRenderHandler<AEssentiaKey> {

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AEssentiaKey stack) {
        String tag = stack.getId().getPath();
        // 渲染要素图标（透明背景）：TC 的 aspect 图标是白色剪影，需用 aspect 颜色着色（与 JEI 的 AspectGuiRenderer 一致）
        int color = getAspectColor(tag);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        guiGraphics.setColor(r, g, b, 1.0f);
        guiGraphics.blit(aspectTexture(tag), x, y, 0, 0, 16, 16, 16, 16);
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** 按 tag 缓存 aspect 贴图 RL，避免每帧 fromNamespaceAndPath。 */
    private static final java.util.Map<String, ResourceLocation> TEXTURE_CACHE = new java.util.HashMap<>();

    private static ResourceLocation aspectTexture(String tag) {
        return TEXTURE_CACHE.computeIfAbsent(tag,
                t -> ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/" + t + ".png"));
    }

    /** 获取 aspect 的 ARGB 颜色（按 tag 查 Thaumcraft Aspect），未知返回白色。 */
    private static int getAspectColor(String tag) {
        thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(tag);
        return aspect != null ? aspect.getColor() : 0xFFFFFFFF;
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