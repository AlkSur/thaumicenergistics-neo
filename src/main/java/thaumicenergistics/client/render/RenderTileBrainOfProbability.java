package thaumicenergistics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import thaumcraft.client.renderers.blockentity.BrainJarBlockEntityRenderer;
import thaumicenergistics.common.blocks.BrainOfProbabilityBlock;
import thaumicenergistics.common.tiles.BrainOfProbabilityBlockEntity;

/**
 * 概率之脑罐腔内脑子的缓慢旋转渲染。脑子模型与贴图直接取自 TC 脑罐渲染器的公开静态组装
 * （{@link BrainJarBlockEntityRenderer#createBrain()} 与 brain2.png），不复制资产；姿态链与
 * 渲染类型逐条对齐 TC 官方 render（entityCutoutNoCull——cull 开启会因翻转姿态剔除全部可见面），
 * 仅旋转源改为 gameTime 驱动。
 */
public class RenderTileBrainOfProbability implements BlockEntityRenderer<BrainOfProbabilityBlockEntity> {

    private static final ResourceLocation BRAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/brain2.png");

    private static final float BRAIN_SCALE = 0.4F;

    private static final float BRAIN_LIFT = -0.8F;

    private static final float BRAIN_DEGREES_PER_TICK = 0.25F;

    private static final float BRAIN_TURN_TICKS = 1440.0F;

    private final ModelPart brain;

    public RenderTileBrainOfProbability(BlockEntityRendererProvider.Context context) {
        this.brain = BrainJarBlockEntityRenderer.createBrain();
    }

    @Override
    public void render(BrainOfProbabilityBlockEntity box, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        // 脑在否以方块状态 JAR 为准（对齐 CE hasJar）——BE 的 brain ItemStack 字段不做网络同步，
        // 客户端读它恒为空会导致脑子只在存档重进后出现。
        if (!box.getBlockState().getValue(BrainOfProbabilityBlock.JAR)) {
            return;
        }
        Level level = box.getLevel();
        long gameTime = level == null ? 0L : level.getGameTime();
        float turn = ((gameTime % (long) BRAIN_TURN_TICKS) + partialTick) * BRAIN_DEGREES_PER_TICK;

        poses.pushPose();
        poses.translate(0.5F, 0.01F, 0.5F);
        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        poses.translate(0.0F, BRAIN_LIFT, 0.0F);
        poses.mulPose(Axis.YP.rotationDegrees(turn));
        poses.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poses.scale(BRAIN_SCALE, BRAIN_SCALE, BRAIN_SCALE);
        this.brain.render(
                poses, buffers.getBuffer(RenderType.entityCutoutNoCull(BRAIN_TEXTURE)), light, overlay);
        poses.popPose();
    }
}
