package thaumicenergistics.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.items.*;

public final class ModItems {
    private ModItems() {}
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThaumicEnergistics.MODID);

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_1K = REGISTRY.registerItem("essentia_cell_1k", ItemEssentiaCell::create1k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_4K = REGISTRY.registerItem("essentia_cell_4k", ItemEssentiaCell::create4k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_16K = REGISTRY.registerItem("essentia_cell_16k", ItemEssentiaCell::create16k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_64K = REGISTRY.registerItem("essentia_cell_64k", ItemEssentiaCell::create64k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_CREATIVE = REGISTRY.registerItem("essentia_cell_creative", ItemEssentiaCell::createCreative);
    public static final DeferredItem<Item> STORAGE_COMPONENT_1K = REGISTRY.registerSimpleItem("storage_component_1k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_4K = REGISTRY.registerSimpleItem("storage_component_4k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_16K = REGISTRY.registerSimpleItem("storage_component_16k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_64K = REGISTRY.registerSimpleItem("storage_component_64k");
    public static final DeferredItem<Item> STORAGE_CASING = REGISTRY.registerSimpleItem("storage_casing");
    public static final DeferredItem<Item> COALESCENCE_CORE = REGISTRY.registerSimpleItem("coalescence_core");
    public static final DeferredItem<Item> DIFFUSION_CORE = REGISTRY.registerSimpleItem("diffusion_core");
    public static final DeferredItem<Item> KNOWLEDGE_CORE = REGISTRY.register("knowledge_core",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ItemFocusAEWrench> FOCUS_AEWRENCH = REGISTRY.register("focus_aewrench",
            () -> new ItemFocusAEWrench());
    public static final DeferredItem<WirelessEssentiaTerminalItem> WIRELESS_ESSENTIA_TERMINAL = REGISTRY.register(
            "wireless_essentia_terminal",
            () -> new WirelessEssentiaTerminalItem(
                    appeng.core.AEConfig.instance().getWirelessTerminalBattery(),
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ItemGolemWirelessBackpack> GOLEM_WIFI_BACKPACK = REGISTRY.register("golem_wifi_backpack",
            ItemGolemWirelessBackpack::new);

    public static final DeferredItem<PartItemEssentiaImportBus> ESSENTIA_IMPORT_BUS =
            REGISTRY.register(
                    "essentia_import_bus",
                    () -> new PartItemEssentiaImportBus(new Item.Properties().stacksTo(64))
            );

    public static final DeferredItem<PartItemEssentiaExportBus> ESSENTIA_EXPORT_BUS =
            REGISTRY.register(
                    "essentia_export_bus",
                    () -> new PartItemEssentiaExportBus(new Item.Properties().stacksTo(64))
            );

    public static final DeferredItem<PartItemEssentiaStorageBus> ESSENTIA_STORAGE_BUS =
            REGISTRY.register(
                    "essentia_storage_bus",
                    () -> new PartItemEssentiaStorageBus(new Item.Properties().stacksTo(64))
            );

    public static final DeferredItem<PartItemEssentiaTerminal> ESSENTIA_TERMINAL_ITEM =
            REGISTRY.register(
                    "essentia_terminal",
                    () -> new PartItemEssentiaTerminal(new Item.Properties().stacksTo(64))
            );

    public static final DeferredItem<PartItemArcaneCraftingTerminal> ARCANE_CRAFTING_TERMINAL_ITEM =
            REGISTRY.register("arcane_crafting_terminal",
                    () -> new PartItemArcaneCraftingTerminal(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<PartItemEssentiaLevelEmitter> ESSENTIA_LEVEL_EMITTER =
            REGISTRY.register("essentia_level_emitter",
                    () -> new PartItemEssentiaLevelEmitter(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<PartItemVisInterface> VIS_INTERFACE =
            REGISTRY.register("vis_interface",
                    () -> new PartItemVisInterface(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_PROVIDER);
    public static final DeferredItem<BlockItem> INFUSION_PROVIDER_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.INFUSION_PROVIDER);
    public static final DeferredItem<BlockItem> ARCANE_ASSEMBLER_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.ARCANE_ASSEMBLER);
    public static final DeferredItem<BlockItem> ESSENTIA_VIBRATION_CHAMBER_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_VIBRATION_CHAMBER);
    public static final DeferredItem<BlockItem> KNOWLEDGE_INSCRIBER_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.KNOWLEDGE_INSCRIBER);
    public static final DeferredItem<BlockItem> ESSENTIA_CELL_WORKBENCH_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_CELL_WORKBENCH);
    public static final DeferredItem<BlockItem> DISTILLATION_ENCODER_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.DISTILLATION_ENCODER);
    public static final DeferredItem<BlockItem> INFUSION_MONITOR_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.INFUSION_MONITOR);
    public static final DeferredItem<BlockItem> ALKUSURE86_FUMO_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.ALKUSURE86_FUMO);
    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER_CONNECTION_ITEM =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_PROVIDER_CONNECTION);

    /** 无线连接器：右键接收器选中，再右键源质供应器建立连接。 */
    public static final DeferredItem<net.minecraft.world.item.Item> WIRELESS_CONNECTOR =
            REGISTRY.register("wireless_connector",
                    () -> new thaumicenergistics.common.items.WirelessConnectorItem(new net.minecraft.world.item.Item.Properties()));
}