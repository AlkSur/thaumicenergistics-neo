package tc_energy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.client.AspectGuiRender;
import tc_energy.common.integration.appeng.AEssentiaKey;

import java.util.ArrayList;
import java.util.List;

public class EssentiaKeyRenderHandler implements appeng.api.client.AEKeyRenderHandler<AEssentiaKey> {

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AEssentiaKey stack) {
        // 复用 Ren 版 TC 的 AspectGuiRender.blit（blend 状态正确），与蒸馏编码台一致。
        AspectId aspect = AspectId.of(stack.getId());
        if (AspectApi.contains(aspect)) {
            AspectGuiRender.blit(guiGraphics, aspect, x, y, 1.0f);
        }
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, AEssentiaKey what,
                                float scale, int combinedLight, Level level) {
    }

    @Override
    public Component getDisplayName(AEssentiaKey stack) {
        return stack.getDisplayName(); // AEssentiaKey.computeDisplayName() 返回源质真实名称
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey stack) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getDisplayName());
        tooltip.add(Component.literal(appeng.util.Platform.formatModName(stack.getModId())));
        return tooltip;
    }
}
