package thaumicenergistics.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import thaumcraft.client.renderers.entity.ThaumcraftGolemRenderer;
import thaumcraft.common.registry.TCEntityTypes;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.render.layer.GolemBackpackLayer;
import thaumicenergistics.client.render.model.GolemBackpackModels;
import thaumicenergistics.client.render.RenderTileArcaneAssembler;
import thaumicenergistics.client.render.RenderTileBrainOfProbability;
import thaumicenergistics.client.render.RenderTileEVC;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

/** 客户端渲染注册的事件处理器。 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_ASSEMBLER.get(), RenderTileArcaneAssembler::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), RenderTileEVC::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BRAIN_OF_PROBABILITY.get(), RenderTileBrainOfProbability::new);
    }

    /** Reload the CE per-face model without baking it into the block texture atlas. */
    @SubscribeEvent
    public static void registerBackpackReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GolemBackpackModels::reload);
    }

    @SubscribeEvent
    public static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
        // 给 Thaumcraft 的傀儡渲染器添加背包渲染层
        EntityRenderer<?> renderer = event.getRenderer(TCEntityTypes.GOLEM.get());
        if (renderer instanceof ThaumcraftGolemRenderer golemRenderer) {
            golemRenderer.addLayer(new GolemBackpackLayer(golemRenderer));
        }
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // T-M2-REMOVE：源质终端与无线源质终端已删除，原两段紫色着色注册随之移除。
    }
}
