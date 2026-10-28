package thaumicenergistics.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.ae2wt.AE2WTCompat;
import thaumicenergistics.common.items.*;
import appeng.core.AEConfig;
import thaumicenergistics.common.items.WirelessConnectorItem;

public final class ModItems {
    private ModItems() {}
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThaumicEnergistics.MODID);

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_1K = REGISTRY.registerItem("essentia_cell_1k", ItemEssentiaCell::create1k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_4K = REGISTRY.registerItem("essentia_cell_4k", ItemEssentiaCell::create4k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_16K = REGISTRY.registerItem("essentia_cell_16k", ItemEssentiaCell::create16k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_64K = REGISTRY.registerItem("essentia_cell_64k", ItemEssentiaCell::create64k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_256K = REGISTRY.registerItem("essentia_cell_256k", ItemEssentiaCell::create256k);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_1M = REGISTRY.registerItem("essentia_cell_1m", ItemEssentiaCell::create1m);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_4M = REGISTRY.registerItem("essentia_cell_4m", ItemEssentiaCell::create4m);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_16M = REGISTRY.registerItem("essentia_cell_16m", ItemEssentiaCell::create16m);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_64M = REGISTRY.registerItem("essentia_cell_64m", ItemEssentiaCell::create64m);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_256M = REGISTRY.registerItem("essentia_cell_256m", ItemEssentiaCell::create256m);
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_CREATIVE = REGISTRY.registerItem("essentia_cell_creative", ItemEssentiaCell::createCreative);
    public static final DeferredItem<Item> STORAGE_COMPONENT_1K = REGISTRY.registerSimpleItem("storage_component_1k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_4K = REGISTRY.registerSimpleItem("storage_component_4k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_16K = REGISTRY.registerSimpleItem("storage_component_16k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_64K = REGISTRY.registerSimpleItem("storage_component_64k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_256K = REGISTRY.registerSimpleItem("storage_component_256k");
    public static final DeferredItem<Item> STORAGE_COMPONENT_1M = REGISTRY.registerSimpleItem("storage_component_1m");
    public static final DeferredItem<Item> STORAGE_COMPONENT_4M = REGISTRY.registerSimpleItem("storage_component_4m");
    public static final DeferredItem<Item> STORAGE_COMPONENT_16M = REGISTRY.registerSimpleItem("storage_component_16m");
    public static final DeferredItem<Item> STORAGE_COMPONENT_64M = REGISTRY.registerSimpleItem("storage_component_64m");
    public static final DeferredItem<Item> STORAGE_COMPONENT_256M = REGISTRY.registerSimpleItem("storage_component_256m");
    public static final DeferredItem<Item> STORAGE_CASING = REGISTRY.registerSimpleItem("storage_casing");
    public static final DeferredItem<Item> COALESCENCE_CORE = REGISTRY.registerSimpleItem("coalescence_core");
    public static final DeferredItem<Item> DIFFUSION_CORE = REGISTRY.registerSimpleItem("diffusion_core");
    public static final DeferredItem<Item> KNOWLEDGE_CORE = REGISTRY.register("knowledge_core",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ItemFocusAEWrench> FOCUS_AEWRENCH = REGISTRY.register("focus_aewrench",
            () -> new ItemFocusAEWrench());
    /**
     * 无线奥术合成终端：注册 ID 恒定，按运行环境选实现——ae2wtlib 完整模组存在时用
     * {@code ItemWT} 体系实现（纳入通用终端合并/模式切换），否则用独立实现。
     * 两实现参数一致（AEConfig 无线终端电池 + stacksTo(1)）。
     */
    public static final DeferredItem<Item> WIRELESS_ARCANE_CRAFTING_TERMINAL =
            REGISTRY.register("wireless_arcane_crafting_terminal",
                    () -> ModList.get().isLoaded("ae2wtlib")
                            ? AE2WTCompat.createTerminalItem()
                            : new WirelessArcaneCraftingTerminalItem(
                                    AEConfig.instance().getWirelessTerminalBattery(),
                                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<VisConnectorCardItem> VIS_CONNECTOR_CARD = REGISTRY.register(
            "vis_connector_card",
            () -> new VisConnectorCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ItemGolemWirelessBackpack> GOLEM_WIFI_BACKPACK = REGISTRY.register("golem_wifi_backpack",
            ItemGolemWirelessBackpack::new);

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
    public static final DeferredItem<BlockItem> MAIQILIN_FUMO_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.MAIQILIN_FUMO);
    public static final DeferredItem<BlockItem> BEIWUCN_FUMO_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.BEIWUCN_FUMO);
    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER_CONNECTION_ITEM =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_PROVIDER_CONNECTION);

    /** M6：概率加速卡（手持右键概率之脑直插，上限 4）。 */
    public static final DeferredItem<ProbabilityAcceleratorCardItem> PROBABILITY_ACCELERATOR_CARD =
            REGISTRY.register("probability_accelerator_card",
                    () -> new ProbabilityAcceleratorCardItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<BlockItem> BRAIN_OF_PROBABILITY_ITEM =
            REGISTRY.registerSimpleBlockItem(ModBlocks.BRAIN_OF_PROBABILITY);
    public static final DeferredItem<BlockItem> BRAIN_OF_PROBABILITY_TOP_ITEM =
            REGISTRY.registerSimpleBlockItem(ModBlocks.BRAIN_OF_PROBABILITY_TOP);

    /** 无线连接器：右键接收器选中，再右键源质供应器建立连接。 */
    public static final DeferredItem<net.minecraft.world.item.Item> WIRELESS_CONNECTOR =
            REGISTRY.register("wireless_connector",
                    () -> new WirelessConnectorItem(new net.minecraft.world.item.Item.Properties()));

    /**
     * 源质访问卡：插入 AE2 原版输入/输出/存储总线与 ME 接口后，使其获得源质能力。
     * 走 AE2 升级卡基类 {@link thaumicenergistics.common.items.ItemEssentiaUpgradeCard}（= AE2 工厂函数等价物）。
     */
    public static final DeferredItem<Item> ESSENTIA_UPGRADE_CARD = REGISTRY.register("essentia_upgrade_card",
            () -> new ItemEssentiaUpgradeCard(new Item.Properties()));
}
