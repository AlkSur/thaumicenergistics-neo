package thaumicenergistics.client;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import thaumcraft.client.renderers.entity.ThaumcraftGolemRenderer;
import thaumcraft.common.registry.TCEntityTypes;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.render.layer.GolemBackpackLayer;
import thaumicenergistics.client.render.model.GolemBackpackModel;
import thaumicenergistics.client.render.RenderTileArcaneAssembler;
import thaumicenergistics.client.render.RenderTileBrainOfProbability;
import thaumicenergistics.client.render.RenderTileEVC;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/** 客户端渲染注册的事件处理器。 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    /** 背包模型的 LayerDefinition 注册 key */
    public static final ModelLayerLocation GOLEM_BACKPACK_LAYER =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "golem_backpack"),
                    "main");

    @SubscribeEvent
    public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_ASSEMBLER.get(), RenderTileArcaneAssembler::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), RenderTileEVC::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BRAIN_OF_PROBABILITY.get(), RenderTileBrainOfProbability::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GOLEM_BACKPACK_LAYER, GolemBackpackModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
        // 给 Thaumcraft 的傀儡渲染器添加背包渲染层
        EntityRenderer<?> renderer = event.getRenderer(TCEntityTypes.GOLEM.get());
        if (renderer instanceof ThaumcraftGolemRenderer golemRenderer) {
            EntityModelSet modelSet = event.getEntityModels();
            ModelPart backpackPart = modelSet.bakeLayer(GOLEM_BACKPACK_LAYER);
            GolemBackpackModel backpackModel = new GolemBackpackModel(backpackPart);
            golemRenderer.addLayer(new GolemBackpackLayer(golemRenderer, backpackModel));
        }
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // T-M2-REMOVE：源质终端与无线源质终端已删除，原两段紫色着色注册随之移除。
    }
}
