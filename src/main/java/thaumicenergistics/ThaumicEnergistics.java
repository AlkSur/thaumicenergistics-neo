package thaumicenergistics;
//
//AlkSur
import appeng.api.features.GridLinkables;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.style.StyleManager;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import appeng.api.AECapabilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumicenergistics.common.container.*;
import thaumicenergistics.common.features.RecipeRegistration;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.integration.appeng.ThEAppliedEnergistics;
import thaumicenergistics.common.integration.tc.ThEThaumcraft;
import thaumicenergistics.common.items.ItemGolemWirelessBackpack;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.common.parts.VisInterfacePart;
import thaumicenergistics.init.*;
import appeng.api.networking.GridServices;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.items.tools.powered.WirelessTerminalItem;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.client.ClientInit;
import thaumicenergistics.common.blocks.ThEWrenchHandler;
import thaumicenergistics.common.grid.EssentiaGridService;
import thaumicenergistics.common.network.ThENetworking;

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
        modBus.addListener(this::registerScreens);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::registerUpgrades);

        if (FMLLoader.getDist().isClient()) {
            modBus.addListener(ClientInit::onClientSetup);
        }
        ThEAppliedEnergistics.init();

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
            ThEThaumcraft.init();
            P2PTunnelAttunement.registerAttunementTag(ModItems.VIS_INTERFACE.get());
            GridLinkables.register(ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            // T-FIX-BATCH ①：无线访问点链接槽按 GridLinkables 表过滤（RestrictedInputSlot.GRID_LINKABLE_ITEM
            // → GridLinkables.get(item) != null），漏注册则终端放不进访问点槽位。
            GridLinkables.register(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
        });
    }

    private void registerUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 神秘装配台：加速卡（AEItems.SPEED_CARD = Acceleration Card，tooltip 名单 + 装配台升级槽可装——加快合成）
            Upgrades.add(AEItems.SPEED_CARD, ModItems.ARCANE_ASSEMBLER_ITEM.get(), 4);

            // T-M1-006-①：源质访问卡关联 AE2 原版四宿主——
            // 使卡可被四宿主的升级卡槽容许插入，且卡 tooltip 的「Supported By」行非空。
            // 四宿主（清单口径）：输入总线 / 输出总线 / 存储总线 / ME 接口。
            // ME 接口 = 1 个宿主概念、2 种形态（部件 AEParts.INTERFACE + 方块 AEBlocks.INTERFACE）；
            // AE2 官方 InitUpgrades 对两形态都注册（InitUpgrades.java:44-47），漏方块形态会导致其无法插卡。
            var essentiaCard = ModItems.ESSENTIA_UPGRADE_CARD.get();
            Upgrades.add(essentiaCard, AEParts.IMPORT_BUS, 1);
            Upgrades.add(essentiaCard, AEParts.EXPORT_BUS, 1);
            Upgrades.add(essentiaCard, AEParts.STORAGE_BUS, 1);
            // MGMT-TOOLTIP：part/block 两形态翻译键不同但显示名相同（"ME接口"），AE2 tooltip 去重按
            // 翻译键 Component 比较——须传官方组键合并（对齐 InitUpgrades.java:44-47 自家卡写法）。
            var interfaceGroup = GuiText.Interface.getTranslationKey();
            Upgrades.add(essentiaCard, AEParts.INTERFACE, 1, interfaceGroup);
            Upgrades.add(essentiaCard, AEBlocks.INTERFACE, 1, interfaceGroup);

            // T-M1-003 P3：源质卡关联 AE2 原版样板供应器两形态（部件 + 方块）。
            // 卡槽本身由 mixins/ae2/provider/ 组注入（AppFlux 存在时由 AppFlux 提供）；
            // 本注册只负责「让卡能插进该宿主的升级槽 + Supported By 行含供应器」。
            // 对齐 AE2 官方 InitUpgrades 对 interface 两形态成对注册的先例（InitUpgrades.java:44-47）。
            // MGMT-TOOLTIP：GuiText 无 PatternProvider 常量，取官方 CraftingInterface（显示文本
            // 即 "ME Pattern Provider"/"ME样板供应器"，AE2 lang 双语实证）作组键。
            var patternProviderGroup = GuiText.CraftingInterface.getTranslationKey();
            Upgrades.add(essentiaCard, AEParts.PATTERN_PROVIDER, 1, patternProviderGroup);
            Upgrades.add(essentiaCard, AEBlocks.PATTERN_PROVIDER, 1, patternProviderGroup);

            // T-M3-VISCARD-REV：vis 连接卡（升级卡）仅关联无线奥术合成终端——
            // 允许插入其升级槽（2 槽，AE2 无线终端白送），并在 tooltip「Supported By」行显示宿主。
            Upgrades.add(ModItems.VIS_CONNECTOR_CARD.get(), ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 1);

            // WTBRIDGE：量子桥卡准入适配（软依赖）——ae2wtlib 的 UpgradeHelper 只对注册进其
            // AddTerminalEvent 体系的终端映射卡表，我方 WACT 未注册（ItemWT 类层级硬绑死、单继承无解），
            // 故自行补一条 Upgrades.add 映射放行插入（AE2 升级槽准入 = Upgrades.getMaxInstallable）。
            if (ModList.get().isLoaded(WirelessArcaneCraftingTerminalMenuHost.AE2WTLIB_MODID)) {
                var bridgeCard = BuiltInRegistries.ITEM
                        .getOptional(WirelessArcaneCraftingTerminalMenuHost.AE2WTLIB_QUANTUM_BRIDGE_CARD).orElse(null);
                if (bridgeCard != null && bridgeCard != Items.AIR) {
                    Upgrades.add(bridgeCard, ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 1);
                }
            }

            // T-M6-IMPL：概率加速卡关联概率之脑（白名单 + Supported By 行；
            // 实际装填走方块右键直插，上限 4 由 BE 记账）。
            Upgrades.add(ModItems.PROBABILITY_ACCELERATOR_CARD.get(), ModItems.BRAIN_OF_PROBABILITY_ITEM.get(), 4);
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

        // 无线奥术合成终端：复用同一屏幕类与样式（共用菜单类）
        event.register(ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                (ContainerArcaneCraftingTerminal menu, Inventory inv, Component title)
                        -> new GuiArcaneCraftingTerminal(menu, inv, title,
                        StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal.json")));

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
        // M6 概率之脑：ME 网络节点 capability（扣 cognitio/能量、周期 tick 依赖）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.BRAIN_OF_PROBABILITY.get(),
                (be, context) -> (appeng.api.networking.IInWorldGridNodeHost) be);
    }
}
