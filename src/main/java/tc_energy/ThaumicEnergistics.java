package tc_energy;
//
//AlkSur
import appeng.api.features.GridLinkables;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.style.StyleManager;
import appeng.core.definitions.AEItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import appeng.api.AECapabilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tc_energy.common.container.*;
import tc_energy.common.features.RecipeRegistration;
import tc_energy.common.integration.appeng.AEssentiaKeyType;
import tc_energy.common.integration.appeng.ThEAppliedEnergistics;
import tc_energy.common.integration.tc.ThEThaumcraft;
import tc_energy.common.items.ItemFocusAEWrench;
import tc_energy.common.items.ItemGolemWirelessBackpack;
import tc_energy.common.parts.ArcaneCraftingTerminalPart;
import tc_energy.common.parts.EssentiaExportBusPart;
import tc_energy.common.parts.EssentiaImportBusPart;
import tc_energy.common.parts.EssentiaLevelEmitterPart;
import tc_energy.common.parts.EssentiaStorageBusPart;
import tc_energy.common.parts.EssentiaTerminalPart;
import tc_energy.common.parts.VisInterfacePart;
import tc_energy.init.*;

@Mod(ThaumicEnergistics.MODID)
public final class ThaumicEnergistics {
    public static final String MODID = "tc_energy";
    public static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics");

    public ThaumicEnergistics(IEventBus modBus) {
        LOG.info("ThaumicEnergistics loading...");
        ModBlocks.REGISTRY.register(modBus);
        ModItems.REGISTRY.register(modBus);
        ModBlockEntities.REGISTRY.register(modBus);
        ModMenuTypes.REGISTRY.register(modBus);
        ModCreativeTab.REGISTRY.register(modBus);
        modBus.addListener(this::registerScreens);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::registerUpgrades);

        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
            modBus.addListener(tc_energy.client.ClientInit::onClientSetup);
        }
        ThEAppliedEnergistics.init();

        appeng.api.networking.GridServices.register(
                tc_energy.api.grid.IEssentiaGrid.class,
                tc_energy.common.grid.EssentiaGridService.class
        );

        tc_energy.common.network.ThENetworking.init(modBus);


        tc_energy.common.blocks.ThEWrenchHandler.register();

        modBus.addListener(this::registerCapabilities);

        PartModels.registerModels(
                EssentiaImportBusPart.RL_BASE, EssentiaImportBusPart.RL_OFF,
                EssentiaImportBusPart.RL_HAS_CHANNEL, EssentiaImportBusPart.RL_ON
        );
        PartModels.registerModels(
                EssentiaExportBusPart.RL_BASE, EssentiaExportBusPart.RL_OFF,
                EssentiaExportBusPart.RL_HAS_CHANNEL, EssentiaExportBusPart.RL_ON
        );
        PartModels.registerModels(
                EssentiaStorageBusPart.RL_BASE, EssentiaStorageBusPart.RL_OFF,
                EssentiaStorageBusPart.RL_HAS_CHANNEL, EssentiaStorageBusPart.RL_ON
        );
        PartModels.registerModels(
                EssentiaTerminalPart.RL_BASE, EssentiaTerminalPart.RL_OFF, EssentiaTerminalPart.RL_ON
        );
        PartModels.registerModels(
                ArcaneCraftingTerminalPart.RL_BASE, ArcaneCraftingTerminalPart.RL_OFF,
                ArcaneCraftingTerminalPart.RL_HAS_CHANNEL, ArcaneCraftingTerminalPart.RL_ON
        );
        PartModels.registerModels(
                EssentiaLevelEmitterPart.RL_BASE_OFF, EssentiaLevelEmitterPart.RL_BASE_ON,
                EssentiaLevelEmitterPart.RL_STATUS_HAS_CHANNEL, EssentiaLevelEmitterPart.RL_STATUS_ON,
                EssentiaLevelEmitterPart.RL_STATUS_OFF
        );
        PartModels.registerModels(VisInterfacePart.MODEL_VIS_INTERFACE);

        LOG.info("ThaumicEnergistics setup complete");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                ModBlockEntities.ESSENTIA_PROVIDER.get(), ModItems.ESSENTIA_PROVIDER_ITEM.get());
        appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                ModBlockEntities.INFUSION_PROVIDER.get(), ModItems.INFUSION_PROVIDER_ITEM.get());

        event.enqueueWork(() -> {
            RecipeRegistration.registerAll();
            // 注册 AE 扳手核心的施法动作（法杖装核心后 shift+右键拆卸 AE 方块）
            ItemFocusAEWrench.registerFocusAction();
            ThEThaumcraft.init();
            tc_energy.common.integration.tc.ThEVisNetworkSetup.register();
            P2PTunnelAttunement.registerAttunementTag(ModItems.VIS_INTERFACE.get());
            GridLinkables.register(ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            GridLinkables.register(ModItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);
        });
    }

    private void registerUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 注：不传 group——带 group 会在卡 tooltip 中与 AE2 原版 IO 总线合并显示为
            // "ME Import/Export Bus"（同 group 跳过），ThE 设备名不显示。与存储总线一致，各自显示设备名。
            var importBusItem = ModItems.ESSENTIA_IMPORT_BUS.get();
            Upgrades.add(AEItems.FUZZY_CARD,    importBusItem, 1);
            Upgrades.add(AEItems.REDSTONE_CARD, importBusItem, 1);
            Upgrades.add(AEItems.CAPACITY_CARD, importBusItem, 5);
            Upgrades.add(AEItems.SPEED_CARD,    importBusItem, 4);
            Upgrades.add(AEItems.INVERTER_CARD, importBusItem, 1);

            var exportBusItem = ModItems.ESSENTIA_EXPORT_BUS.get();
            Upgrades.add(AEItems.FUZZY_CARD,     exportBusItem, 1);
            Upgrades.add(AEItems.REDSTONE_CARD,  exportBusItem, 1);
            Upgrades.add(AEItems.CAPACITY_CARD,  exportBusItem, 5);
            Upgrades.add(AEItems.SPEED_CARD,     exportBusItem, 4);
            Upgrades.add(AEItems.CRAFTING_CARD,  exportBusItem, 1);

            var storageBusItem = ModItems.ESSENTIA_STORAGE_BUS.get();
            Upgrades.add(AEItems.FUZZY_CARD,     storageBusItem, 1);
            Upgrades.add(AEItems.INVERTER_CARD,  storageBusItem, 1);
            Upgrades.add(AEItems.CAPACITY_CARD,  storageBusItem, 5);
            Upgrades.add(AEItems.VOID_CARD,      storageBusItem, 1);

            // 神秘装配台：加速卡（AEItems.SPEED_CARD = Acceleration Card，tooltip 名单 + 装配台升级槽可装——加快合成）
            Upgrades.add(AEItems.SPEED_CARD, ModItems.ARCANE_ASSEMBLER_ITEM.get(), 4);
        });
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), GuiEssentiaCellWorkbench::new);
        event.register(ModMenuTypes.EVC.get(), GuiEssentiaVibrationChamber::new);
        event.register(ModMenuTypes.ARCANE_ASSEMBLER.get(), GuiArcaneAssembler::new);
        event.register(ModMenuTypes.ESSENTIA_TERMINAL.get(),
                (EssentiaTerminalMenu menu, Inventory inv, Component title)
                        -> new GuiEssentiaTerminal(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/terminals/terminal.json")));
        event.register(ModMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get(),
                (EssentiaTerminalMenu menu, Inventory inv, Component title)
                        -> new GuiEssentiaTerminal(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")));
        event.register(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), GuiKnowledgeInscriber::new);
        event.register(ModMenuTypes.DISTILLATION_ENCODER.get(), GuiDistillationPatternEncoder::new);

        event.register(ModMenuTypes.ESSENTIA_IMPORT_BUS.get(),
                (ContainerEssentiaImportBus menu, Inventory inv, Component title)
                        -> new GuiEssentiaImportBus(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/import_bus.json")));

        event.register(ModMenuTypes.ESSENTIA_EXPORT_BUS.get(),
                (ContainerEssentiaExportBus menu, Inventory inv, Component title)
                        -> new GuiEssentiaExportBus(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/export_bus.json")));

        event.register(ModMenuTypes.ESSENTIA_STORAGE_BUS.get(),
                (ContainerEssentiaStorageBus menu, Inventory inv, Component title)
                        -> new GuiEssentiaStorageBus(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/storage_bus.json")));

        event.register(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                (ContainerArcaneCraftingTerminal menu, Inventory inv, Component title)
                        -> new GuiArcaneCraftingTerminal(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal.json")));

        event.register(ModMenuTypes.ESSENTIA_LEVEL_EMITTER.get(),
                (ContainerEssentiaLevelEmitter menu, Inventory inv, Component title)
                        -> new GuiEssentiaLevelEmitter(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/essentia_level_emitter.json")));
    }

    private void registerKeyTypes(net.neoforged.neoforge.registries.RegisterEvent event) {
        LOG.info("registerKeyTypes fired, registryKey={}", event.getRegistryKey());
        if (event.getRegistryKey() == AEKeyType.REGISTRY_KEY) {
            AEKeyTypesInternal.register(AEssentiaKeyType.INSTANCE);
            LOG.info("AEssentiaKeyType registered successfully!");
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        LOG.info("Registering AE2 grid node capabilities...");
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ESSENTIA_PROVIDER.get(),
                (be, context) -> (appeng.api.networking.IInWorldGridNodeHost) be);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (be, context) -> (appeng.api.networking.IInWorldGridNodeHost) be);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (be, context) -> (appeng.api.networking.IInWorldGridNodeHost) be);
        // 装配器也实现 IInWorldGridNodeHost，必须注册 capability，否则 AE2 线缆连不上 →
        // 装配器孤立成自己的网格，ME 终端看不到它提供的可合成配方。
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                (be, context) -> (appeng.api.networking.IInWorldGridNodeHost) be);
        // 注魔监视器：连接 AE 网络（network 状态）需要网格节点 capability
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.INFUSION_MONITOR.get(),
                (be, context) -> (appeng.api.networking.IInWorldGridNodeHost) be);
    }
}
