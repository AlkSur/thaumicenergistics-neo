package thaumicenergistics.common.integration.neoecoae;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.blocks.ArcaneCraftingChamberBlock;
import thaumicenergistics.common.container.ContainerArcaneCraftingChamber;
import thaumicenergistics.common.tiles.TileArcaneCraftingChamber;

/**
 * Content that exists only with Neo ECO AE Extension: the essentia storage matrices for ECO drives (E3) and the
 * arcane crafting chambers (E8). This class
 * names no ECO type, so TE can load it without ECO; every ECO class is behind {@link ThENeoEcoStorage}.
 */
public final class NeoEcoItems {
    private NeoEcoItems() { }

    /** The ECO classes the matrices use; an older ECO without any of them leaves the matrices out. */
    private static final List<String> REQUIRED = List.of(
            "cn.dancingsnow.neoecoae.api.storage.ECOStorageCells",
            "cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem",
            "cn.dancingsnow.neoecoae.api.storage.INativeMEStorageRestrictedItem",
            "cn.dancingsnow.neoecoae.api.storage.ECOCellType");

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ThaumicEnergistics.MODID);
    public static final DeferredHolder<Item, Item> HOUSING = ITEMS.register("eco_essentia_cell_housing",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MATRIX_16M = ITEMS.register("eco_essentia_storage_cell_16m",
            () -> ThENeoEcoStorage.matrix("16m"));
    public static final DeferredHolder<Item, Item> MATRIX_64M = ITEMS.register("eco_essentia_storage_cell_64m",
            () -> ThENeoEcoStorage.matrix("64m"));
    public static final DeferredHolder<Item, Item> MATRIX_256M = ITEMS.register("eco_essentia_storage_cell_256m",
            () -> ThENeoEcoStorage.matrix("256m"));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ThaumicEnergistics.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThaumicEnergistics.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ThaumicEnergistics.MODID);

    private static final BlockBehaviour.Properties CHAMBER_PROPERTIES =
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 12f).sound(SoundType.METAL).noOcclusion();

    public static final DeferredHolder<Block, ArcaneCraftingChamberBlock> CHAMBER_L4 = BLOCKS.register(
            "arcane_crafting_chamber_l4", () -> new ArcaneCraftingChamberBlock(CHAMBER_PROPERTIES, 4, NeoEcoItems.CHAMBER_TYPE));
    public static final DeferredHolder<Block, ArcaneCraftingChamberBlock> CHAMBER_L6 = BLOCKS.register(
            "arcane_crafting_chamber_l6", () -> new ArcaneCraftingChamberBlock(CHAMBER_PROPERTIES, 6, NeoEcoItems.CHAMBER_TYPE));
    public static final DeferredHolder<Block, ArcaneCraftingChamberBlock> CHAMBER_L9 = BLOCKS.register(
            "arcane_crafting_chamber_l9", () -> new ArcaneCraftingChamberBlock(CHAMBER_PROPERTIES, 9, NeoEcoItems.CHAMBER_TYPE));
    public static final DeferredHolder<Item, BlockItem> CHAMBER_L4_ITEM = ITEMS.register("arcane_crafting_chamber_l4",
            () -> chamberItem(CHAMBER_L4.get(), Rarity.UNCOMMON));
    public static final DeferredHolder<Item, BlockItem> CHAMBER_L6_ITEM = ITEMS.register("arcane_crafting_chamber_l6",
            () -> chamberItem(CHAMBER_L6.get(), Rarity.RARE));
    public static final DeferredHolder<Item, BlockItem> CHAMBER_L9_ITEM = ITEMS.register("arcane_crafting_chamber_l9",
            () -> chamberItem(CHAMBER_L9.get(), Rarity.EPIC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileArcaneCraftingChamber>> CHAMBER_TYPE =
            BLOCK_ENTITIES.register("arcane_crafting_chamber", () -> BlockEntityType.Builder.<TileArcaneCraftingChamber>of(
                    (pos, state) -> new TileArcaneCraftingChamber(NeoEcoItems.CHAMBER_TYPE.get(), pos, state,
                            state.getBlock() instanceof ArcaneCraftingChamberBlock block ? block.getTier() : 4, state.getBlock()),
                    CHAMBER_L4.get(), CHAMBER_L6.get(), CHAMBER_L9.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerArcaneCraftingChamber>> CHAMBER_MENU =
            MENUS.register("arcane_crafting_chamber", () -> IMenuTypeExtension.create(ContainerArcaneCraftingChamber::new));

    private static BlockItem chamberItem(ArcaneCraftingChamberBlock block, Rarity rarity) {
        return new BlockItem(block, new Item.Properties().rarity(rarity)) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                    List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.thaumicenergistics.arcane_crafting_chamber",
                        TileArcaneCraftingChamber.coreSlots(block.getTier()),
                        thaumicenergistics.config.ThEConfig.chamberParallel(block.getTier()))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        };
    }

    private static boolean enabled;

    /** True when ECO is installed and new enough, so the matrices are registered. */
    public static boolean enabled() {
        return enabled;
    }

    /** Called from the mod constructor: registers the matrices only when ECO has the storage API. */
    public static void register(IEventBus modBus) {
        if (!ModList.get().isLoaded("neoecoae")) return;
        try {
            for (String name : REQUIRED) Class.forName(name, false, NeoEcoItems.class.getClassLoader());
        } catch (ClassNotFoundException older) {
            ThaumicEnergistics.LOG.info("Neo ECOAE has no storage matrix API ({}): essentia matrices are off", older.getMessage());
            return;
        }
        enabled = true;
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ThENeoEcoStorage.registerCellTypes(modBus);
    }

    /** Common setup: hands the matrices to ECO's drives. */
    public static void setup() {
        if (enabled) ThENeoEcoStorage.setup();
    }

    public static void addToTab(CreativeModeTab.Output output) {
        if (!enabled) return;
        output.accept(HOUSING.get());
        output.accept(MATRIX_16M.get());
        output.accept(MATRIX_64M.get());
        output.accept(MATRIX_256M.get());
        output.accept(CHAMBER_L4_ITEM.get());
        output.accept(CHAMBER_L6_ITEM.get());
        output.accept(CHAMBER_L9_ITEM.get());
    }
}
