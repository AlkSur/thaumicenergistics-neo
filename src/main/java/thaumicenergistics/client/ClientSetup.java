package thaumicenergistics.client;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import thaumcraft.client.renderers.entity.LegacyGolemModel;
import thaumcraft.client.renderers.entity.ThaumcraftGolemRenderer;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumcraft.common.registry.TCEntityTypes;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.render.layer.GolemBackpackLayer;
import thaumicenergistics.client.render.model.GolemBackpackModel;
import thaumicenergistics.client.render.RenderTileArcaneAssembler;
import thaumicenergistics.client.render.RenderTileEVC;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

/** 客户端渲染注册的事件处理器。 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    /** 背包模型的 LayerDefinition 注册 key */
    public static final net.minecraft.client.model.geom.ModelLayerLocation GOLEM_BACKPACK_LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "golem_backpack"),
                    "main");

    @SubscribeEvent
    public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_ASSEMBLER.get(), RenderTileArcaneAssembler::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), RenderTileEVC::new);
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
            net.minecraft.client.model.geom.ModelPart backpackPart = modelSet.bakeLayer(GOLEM_BACKPACK_LAYER);
            GolemBackpackModel backpackModel = new GolemBackpackModel(backpackPart);
            golemRenderer.addLayer(new GolemBackpackLayer(golemRenderer, backpackModel));
        }
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // 给源质终端物品着色：完全对齐 AEColor.TRANSPARENT.getVariantByTintIndex()
        event.register((stack, tintIndex) -> {
            return switch (tintIndex) {
                case 0 -> -1;              // 不染色
                case 1 -> 0xFF5a479e;      // Dark: 深紫色
                case 2 -> 0xFF915dcd;      // Medium: 紫色
                case 3 -> 0xFFe2a3e3;      // Bright: 淡紫色
                case 4 -> 0xFFb980d8;      // Medium Bright: 中间紫
                default -> -1;
            };
        }, ModItems.ESSENTIA_TERMINAL_ITEM.get());

        // 给无线源质终端物品着色（与源质终端部件相同的紫色）
        event.register((stack, tintIndex) -> {
            return switch (tintIndex) {
                case 0 -> -1;
                case 1 -> 0xFF5a479e;
                case 2 -> 0xFF915dcd;
                case 3 -> 0xFFe2a3e3;
                case 4 -> 0xFFb980d8;
                default -> -1;
            };
        }, ModItems.WIRELESS_ESSENTIA_TERMINAL.get());
    }
}
