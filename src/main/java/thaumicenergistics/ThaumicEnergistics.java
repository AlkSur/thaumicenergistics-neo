package thaumicenergistics;

//AlkSur
import appeng.api.AECapabilities;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.GridLinkables;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.networking.GridServices;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.client.gui.style.StyleManager;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.parts.automation.ExportBusPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.client.ClientInit;
import thaumicenergistics.client.integration.ae2wt.AE2WTClientCompat;
import thaumicenergistics.common.blocks.ThEWrenchHandler;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.common.container.ContainerEssentiaLevelEmitter;
import thaumicenergistics.common.container.GuiArcaneAssembler;
import thaumicenergistics.common.container.GuiArcaneCraftingTerminal;
import thaumicenergistics.common.container.GuiDistillationPatternEncoder;
import thaumicenergistics.common.container.GuiEssentiaCellWorkbench;
import thaumicenergistics.common.container.GuiEssentiaLevelEmitter;
import thaumicenergistics.common.container.GuiEssentiaVibrationChamber;
import thaumicenergistics.common.container.GuiKnowledgeInscriber;
import thaumicenergistics.common.crafting.ArcanePatternDecoder;
import thaumicenergistics.common.essentia.capability.ExportBusEssentiaTransport;
import thaumicenergistics.common.features.ArchaeologyTemplateModifier;
import thaumicenergistics.common.features.RecipeRegistration;
import thaumicenergistics.common.grid.EssentiaGridService;
import thaumicenergistics.common.integration.advancedae.AdvancedAECompat;
import thaumicenergistics.common.integration.ae2lt.AE2LTCompat;
import thaumicenergistics.common.integration.ae2wt.AE2WTCompat;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.integration.appeng.EssentiaContainerItemStrategy;
import thaumicenergistics.common.integration.extendedae.ThEExtendedAE;
import thaumicenergistics.common.integration.tc.ThEThaumcraft;
import thaumicenergistics.common.items.ItemEssentiaCell;
import thaumicenergistics.common.items.ItemGolemWirelessBackpack;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics.common.network.ThENetworking;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.common.parts.VisInterfacePart;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModComponents;
import thaumicenergistics.init.ModCreativeTab;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.init.ModRecipeSerializers;

@Mod(ThaumicEnergistics.MODID)
public final class ThaumicEnergistics {
    public static final String MODID = "thaumicenergistics";
    public static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics");

    public ThaumicEnergistics(IEventBus modBus) {
        LOG.info("ThaumicEnergistics loading...");
        ModBlocks.REGISTRY.register(modBus);
        ModItems.REGISTRY.register(modBus);
        ModBlockEntities.REGISTRY.register(modBus);
        ModMenuTypes.REGISTRY.register(modBus);
        ModCreativeTab.REGISTRY.register(modBus);
        ModComponents.REGISTRY.register(modBus);
        ModRecipeSerializers.REGISTRY.register(modBus);
        modBus.addListener((RegisterEvent event) -> event.register(
                NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                id("archaeology_template"), () -> ArchaeologyTemplateModifier.CODEC));
        modBus.addListener(this::registerScreens);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::registerUpgrades);

        if (FMLLoader.getDist().isClient()) {
            modBus.addListener(ClientInit::onClientSetup);
        }

            // 在构造阶段登记 AE2WT 终端功能；缺少库时保留独立终端。
        if (ModList.get().isLoaded("ae2wtlib")) {
            AE2WTCompat.registerArcaneTerminal();
        }

        GridServices.register(
                IEssentiaGrid.class,
                EssentiaGridService.class
        );

        ThENetworking.init(modBus);

        ThEWrenchHandler.register();

        modBus.addListener(this::registerCapabilities);

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
        AEBaseBlockEntity.registerBlockEntityItem(
                ModBlockEntities.ESSENTIA_PROVIDER.get(), ModItems.ESSENTIA_PROVIDER_ITEM.get());
        AEBaseBlockEntity.registerBlockEntityItem(
                ModBlockEntities.INFUSION_PROVIDER.get(), ModItems.INFUSION_PROVIDER_ITEM.get());

        event.enqueueWork(() -> {
            RecipeRegistration.registerAll();
            PatternDetailsHelper.registerDecoder(new ArcanePatternDecoder());
            ContainerItemStrategy.register(AEssentiaKeyType.INSTANCE,
                    AEssentiaKey.class,
                    new EssentiaContainerItemStrategy());
            ThEThaumcraft.init();
            P2PTunnelAttunement.registerAttunementTag(ModItems.VIS_INTERFACE.get());
            GridLinkables.register(ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            // 注册无线访问点的可绑定物品。
            GridLinkables.register(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
        });
    }

    private void registerUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 装配器最多安装四张加速卡。
            Upgrades.add(AEItems.SPEED_CARD, ModItems.ARCANE_ASSEMBLER_ITEM.get(), 4);

            // 注册源质卡宿主；方块与部件分别登记。
            var essentiaCard = ModItems.ESSENTIA_UPGRADE_CARD.get();
            Upgrades.add(essentiaCard, AEParts.IMPORT_BUS, 1);
            Upgrades.add(essentiaCard, AEParts.EXPORT_BUS, 1);
            Upgrades.add(essentiaCard, AEParts.STORAGE_BUS, 1);
            // 使用同一提示分组，合并接口的方块与部件条目。
            var interfaceGroup = GuiText.Interface.getTranslationKey();
            Upgrades.add(essentiaCard, AEParts.INTERFACE, 1, interfaceGroup);
            Upgrades.add(essentiaCard, AEBlocks.INTERFACE, 1, interfaceGroup);

            // 卡槽由本模组或 AppFlux 提供；两种形态使用同一官方提示分组。
            var patternProviderGroup = GuiText.CraftingInterface.getTranslationKey();
            Upgrades.add(essentiaCard, AEParts.PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(essentiaCard, AEBlocks.PATTERN_PROVIDER, 1, patternProviderGroup);

            // 可选附属仅在存在时注册宿主。
            ThEExtendedAE.registerEssentiaUpgrades();
            AdvancedAECompat.registerEssentiaUpgrades();
            AE2LTCompat.registerEssentiaUpgrades();

            // 独立终端的 vis 卡准入；AE2WT 体系另由 UpgradeHelper 注册所有终端。
            Upgrades.add(ModItems.VIS_CONNECTOR_CARD.get(), ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 1);

            // 保留桥卡显式准入，与 AE2WT 全终端注册使用相同上限，兼容历史独立宿主。
            if (ModList.get().isLoaded(WirelessArcaneCraftingTerminalMenuHost.AE2WTLIB_MODID)) {
                var bridgeCard = BuiltInRegistries.ITEM
                        .getOptional(WirelessArcaneCraftingTerminalMenuHost.AE2WTLIB_QUANTUM_BRIDGE_CARD).orElse(null);
                if (bridgeCard != null && bridgeCard != Items.AIR) {
                    Upgrades.add(bridgeCard, ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 1);
                }
            }

            // 概率之脑最多安装四张加速卡。
            Upgrades.add(ModItems.PROBABILITY_ACCELERATOR_CARD.get(), ModItems.BRAIN_OF_PROBABILITY_ITEM.get(), 4);

            // Keep AE2's per-item limits while grouping every capacity under one tooltip entry.
            for (var entry : ModItems.REGISTRY.getEntries()) {
                if (entry.get() instanceof ItemEssentiaCell cell) {
                    String group = "tooltip.thaumicenergistics.upgrade_host.essentia_storage_cell";
                    Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
                    Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
                    Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, group);
                    Upgrades.add(AEItems.FUZZY_CARD, cell, 1, group);
                }
            }
        });
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), GuiEssentiaCellWorkbench::new);
        event.register(ModMenuTypes.EVC.get(), GuiEssentiaVibrationChamber::new);
        event.register(ModMenuTypes.ARCANE_ASSEMBLER.get(), GuiArcaneAssembler::new);
        event.register(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), GuiKnowledgeInscriber::new);
        event.register(ModMenuTypes.DISTILLATION_ENCODER.get(), GuiDistillationPatternEncoder::new);

        event.register(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                (ContainerArcaneCraftingTerminal menu, Inventory inv, Component title)
                        -> new GuiArcaneCraftingTerminal(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal.json")));

        // AE2WT 宿主使用可切换的屏幕；独立终端保持原屏幕。
        if (ModList.get().isLoaded("ae2wtlib")) {
            AE2WTClientCompat.registerScreen(event);
        } else {
            event.register(ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    (ContainerArcaneCraftingTerminal menu, Inventory inv, Component title)
                            -> new GuiArcaneCraftingTerminal(menu, inv, title,
                            StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal_wireless_base.json")));
        }

        event.register(ModMenuTypes.ESSENTIA_LEVEL_EMITTER.get(),
                (ContainerEssentiaLevelEmitter menu, Inventory inv, Component title)
                        -> new GuiEssentiaLevelEmitter(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/essentia_level_emitter.json")));
    }

    private void registerKeyTypes(RegisterEvent event) {
        LOG.info("registerKeyTypes fired, registryKey={}", event.getRegistryKey());
        if (event.getRegistryKey() == AEKeyType.REGISTRY_KEY) {
            AEKeyTypesInternal.register(AEssentiaKeyType.INSTANCE);
            LOG.info("AEssentiaKeyType registered successfully!");
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                AEBlockEntities.CABLE_BUS.get(),
                (be, side) -> side == null || !(be.getPart(side) instanceof ExportBusPart) ? null
                        : new ExportBusEssentiaTransport(be, side));
        LOG.info("Registering AE2 grid node capabilities...");
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ESSENTIA_PROVIDER.get(),
                (be, context) -> (IInWorldGridNodeHost) be);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (be, context) -> (IInWorldGridNodeHost) be);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (be, context) -> (IInWorldGridNodeHost) be);
        // 装配器也实现 IInWorldGridNodeHost，必须注册 capability，否则 AE2 线缆连不上 →
        // 装配器孤立成自己的网格，ME 终端看不到它提供的可合成配方。
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                (be, context) -> (IInWorldGridNodeHost) be);
        // 注魔监视器：连接 AE 网络（network 状态）需要网格节点 capability
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.INFUSION_MONITOR.get(),
                (be, context) -> (IInWorldGridNodeHost) be);
        // 概率之脑：ME 网络节点 capability（扣 cognitio/能量、周期 tick 依赖）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.BRAIN_OF_PROBABILITY.get(),
                (be, context) -> (IInWorldGridNodeHost) be);
    }
}
