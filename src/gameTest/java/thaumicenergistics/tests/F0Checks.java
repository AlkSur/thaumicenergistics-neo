package thaumicenergistics.tests;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.InventoryAction;
import appeng.menu.locator.MenuLocators;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.blockentities.ArcaneLampFertilityBlockEntity;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumcraft.common.items.TCFunctionalItems;
import thaumcraft.common.registry.TCEntityTypes;
import thaumcraft.common.registry.TCItems;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.common.container.slot.ArcaneCraftingResultSlot;
import thaumicenergistics.common.entities.GolemBackpackHandler;
import thaumicenergistics.common.essentia.capability.EssentiaContainerStorage;
import thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.common.integration.tc.ThEEssentiaContainers;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.common.tiles.TileEssentiaProvider;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModComponents;
import thaumicenergistics.init.ModItems;

/** Reproductions for the phase-1 F0 batch: each test failed before its fix. */
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class F0Checks {
    private F0Checks() { }

    /** TET-01: TC asks getEssentiaType(null) when a directed query finds nothing. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void providerAnswersDirectionlessQueries(GameTestHelper helper) {
        var providerPos = new BlockPos(1, 1, 1);
        helper.setBlock(providerPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(providerPos, ModBlocks.ESSENTIA_PROVIDER.get());
        // A tube with a jar behind it pulls from the provider every tick (empty network).
        helper.setBlock(providerPos.above(), tc("essentia_tube"));
        helper.setBlock(providerPos.above(2), tc("jar_of_essentia"));
        helper.runAfterDelay(60, () -> {
            TileEssentiaProvider provider = helper.getBlockEntity(providerPos);
            helper.assertTrue(provider.getEssentiaType(null) == null, "Empty network has no essentia type");
            helper.assertTrue(provider.getEssentiaAmount(null) == 0, "Empty network has no essentia");
            helper.succeed();
        });
    }

    /** TEA2-01/02/03 on the wired terminal: pay after checks, consume container ingredients. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void wiredArcaneTerminalSettlesLikeTheWorkbench(GameTestHelper helper) {
        registerRecipes();
        var part = terminalPart(helper);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(part.getMainNode().isActive(), "Terminal network must be active");
            var player = player(helper, "ArcaneWiredTest");
            var wand = chargedWand();
            part.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND).setItemDirect(0, wand);
            var grid = part.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
            grid.setItemDirect(0, new ItemStack(Items.DEAD_BUSH));
            grid.setItemDirect(1, new ItemStack(Items.FLINT));
            var menu = new ContainerArcaneCraftingTerminal(11, player.getInventory(), part);
            menu.slotsChanged(grid.toContainer());
            var result = resultSlot(menu);
            helper.assertTrue(!result.getItem().isEmpty(), "Recipe must show its result");

            int before = vis(part.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND).getStackInSlot(0), Aspect.AIR);
            menu.setCarried(new ItemStack(Items.STONE));
            result.doClick(InventoryAction.PICKUP_OR_SET_DOWN, player);
            helper.assertTrue(vis(part.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND).getStackInSlot(0), Aspect.AIR) == before,
                    "A click that cannot take the result must not cost vis (TEA2-02)");
            helper.assertTrue(grid.getStackInSlot(0).is(Items.DEAD_BUSH), "Nor any ingredient");

            menu.setCarried(ItemStack.EMPTY);
            result.doClick(InventoryAction.PICKUP_OR_SET_DOWN, player);
            helper.assertTrue(menu.getCarried().is(Items.TUBE_CORAL), "Craft must produce the result");
            helper.assertTrue(vis(part.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND).getStackInSlot(0), Aspect.AIR) < before, "Craft must pay vis");
            helper.assertTrue(grid.getStackInSlot(0).isEmpty() && grid.getStackInSlot(1).isEmpty(),
                    "Craft must consume ingredients");

            menu.setCarried(ItemStack.EMPTY);
            grid.setItemDirect(0, new ItemStack(Items.WATER_BUCKET));
            grid.setItemDirect(1, new ItemStack(Items.DEAD_BUSH));
            menu.slotsChanged(grid.toContainer());
            result.doClick(InventoryAction.PICKUP_OR_SET_DOWN, player);
            helper.assertTrue(menu.getCarried().is(Items.SPONGE), "Bucket recipe must craft");
            helper.assertTrue(grid.getStackInSlot(0).is(Items.BUCKET),
                    "A water bucket is used up and leaves its empty bucket (TEA2-03)");
            player.discard();
            helper.succeed();
        });
    }

    /** TEA2-01: the wireless terminal must keep the vis it paid after the menu closes. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void wirelessArcaneTerminalPersistsPaidVis(GameTestHelper helper) {
        registerRecipes();
        var part = terminalPart(helper);
        helper.runAfterDelay(40, () -> {
            var player = player(helper, "ArcaneWirelessTest");
            var terminal = new ItemStack(WirelessChargingChecks.TERMINAL.get());
            player.getInventory().setItem(0, terminal);
            WirelessChargingChecks.chargingNode = part.getMainNode().getNode();
            WirelessChargingChecks.connected = true;
            try {
                var host = (WirelessArcaneCraftingTerminalMenuHost) WirelessChargingChecks.TERMINAL.get()
                        .getMenuHost(player, MenuLocators.forInventorySlot(0), null);
                host.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND).setItemDirect(0, chargedWand());
                int before = vis(storedWand(terminal), Aspect.AIR);
                var grid = host.getSubInventory(ArcaneCraftingTerminalPart.INV_CRAFTING);
                grid.setItemDirect(0, new ItemStack(Items.DEAD_BUSH));
                grid.setItemDirect(1, new ItemStack(Items.FLINT));
                var menu = new ContainerArcaneCraftingTerminal(12, player.getInventory(), host);
                menu.slotsChanged(grid.toContainer());
                resultSlot(menu).doClick(InventoryAction.PICKUP_OR_SET_DOWN, player);
                helper.assertTrue(menu.getCarried().is(Items.TUBE_CORAL), "Wireless craft must produce");
                helper.assertTrue(vis(storedWand(terminal), Aspect.AIR) < before,
                        "Paid vis must be written back to the terminal item (TEA2-01)");
            } finally {
                WirelessChargingChecks.chargingNode = null;
                WirelessChargingChecks.connected = false;
                player.discard();
            }
            helper.succeed();
        });
    }

    /** TEC-03: a storage bus over a tube must not promise more than it stores. */
    @GameTest(template = "empty")
    public static void tubeStorageSimulationIsHonest(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, tc("essentia_tube"));
        var tube = helper.getBlockEntity(pos);
        var storage = new EssentiaContainerStorage(() -> tube, Component.literal("tube"), () -> Direction.UP);
        var key = key(Aspect.WATER);
        var source = IActionSource.empty();
        long simulated = storage.insert(key, 64, Actionable.SIMULATE, source);
        long inserted = storage.insert(key, 64, Actionable.MODULATE, source);
        helper.assertTrue(simulated <= inserted,
                "Simulated " + simulated + " but stored " + inserted + ": the difference would be lost");
        helper.succeed();
    }

    /** TEC-04: the wand recharge pedestal is not essentia storage. */
    @GameTest(template = "empty")
    public static void wandPedestalIsNotEssentiaStorage(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, tc("wand_recharge_pedestal"));
        var pedestal = helper.getBlockEntity(pos);
        var storage = new EssentiaContainerStorage(() -> pedestal, Component.literal("pedestal"), () -> Direction.UP);
        var key = key(Aspect.FIRE);
        helper.assertTrue(storage.insert(key, 10, Actionable.MODULATE, IActionSource.empty()) == 0,
                "The pedestal keeps nothing, so it must accept nothing");
        var listed = new KeyCounter();
        storage.getAvailableStacks(listed);
        helper.assertTrue(listed.isEmpty(), "The pedestal must not show phantom essentia");
        helper.succeed();
    }

    /** TEC-05: a node in a jar is not an essentia jar. */
    @GameTest(template = "empty")
    public static void nodeJarIsNotAnEssentiaJar(GameTestHelper helper) {
        helper.assertTrue(ThEEssentiaContainers.isJar(new ItemStack(TCItems.JAR_OF_ESSENTIA.get())), "Jar is a jar");
        helper.assertFalse(ThEEssentiaContainers.isJar(new ItemStack(TCItems.NODE_IN_A_JAR.get())),
                "Node jar must not be filled with essentia");
        helper.succeed();
    }

    /** TEC-07/08: client ids are checked, and key identity no longer needs a permanent cache. */
    @GameTest(template = "empty")
    public static void essentiaIdsAreValidatedAndInterned(GameTestHelper helper) {
        var air = AspectIds.getAspectId(Aspect.AIR);
        helper.assertTrue(air != null && AspectIds.canonicalId(air) != null, "Registered aspect is valid");
        helper.assertTrue(AspectIds.canonicalId(ResourceLocation.fromNamespaceAndPath("evil", air.getPath())) == null,
                "A foreign namespace must not pass as a registered aspect");
        helper.assertTrue(AspectIds.canonicalId(ResourceLocation.fromNamespaceAndPath("x", "not_an_aspect")) == null,
                "Unknown aspect is invalid");
        var copy = ResourceLocation.fromNamespaceAndPath(air.getNamespace(), air.getPath());
        helper.assertTrue(AEssentiaKey.of(air) == AEssentiaKey.of(copy), "Equal ids share one key");
        helper.assertTrue(new AEssentiaKey(copy).getId() == AEssentiaKey.of(air).getId(),
                "Decoded keys share the id instance AE2's KeyCounter groups by");
        helper.succeed();
    }

    /** TEM-01: stock the machine no longer wants goes back instead of blocking the bus. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void staleExportStockReturnsToNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var cablePos = new BlockPos(1, 1, 1);
        var lampPos = cablePos.above();
        helper.setBlock(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var absoluteCable = helper.absolutePos(cablePos);
        PartHelper.setPart(level, absoluteCable, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(level, absoluteCable, Direction.UP, null, AEParts.EXPORT_BUS.get());
        var life = key(Aspect.LIFE);
        var aqua = key(Aspect.WATER);
        bus.getConfig().setStack(0, new GenericStack(life, 1));
        helper.setBlock(lampPos, tc("arcane_lamp_fertility"));
        ArcaneLampFertilityBlockEntity lamp = helper.getBlockEntity(lampPos);
        var nbt = lamp.saveWithFullMetadata(level.registryAccess());
        nbt.putInt("orientation", Direction.DOWN.get3DDataValue());
        lamp.loadWithComponents(nbt, level.registryAccess());
        Map<AEKey, Long> stored = new HashMap<>();
        stored.put(life, 16L);
        MEStorage inventory = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("stale stock"); }
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                long taken = Math.min(amount, stored.getOrDefault(what, 0L));
                if (mode == Actionable.MODULATE) stored.merge(what, -taken, Long::sum);
                return taken;
            }
            @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                if (!(what instanceof AEssentiaKey)) return 0;
                if (mode == Actionable.MODULATE) stored.merge(what, amount, Long::sum);
                return amount;
            }
        };
        helper.runAfterDelay(40, () -> {
            var grid = bus.getMainNode().getGrid();
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(inventory, 0));
            // Leftover from an earlier recipe: aqua the lamp will never take.
            ((EssentiaTransferBuffer) bus).thE$setTransferBuffer(new AspectStack(aqua.getId(), 5));
            bus.getUpgrades().addItems(new ItemStack(ModItems.ESSENTIA_UPGRADE_CARD.get()));
            grid.getTickManager().alertDevice(bus.getMainNode().getNode());
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(stored.getOrDefault(aqua, 0L) == 5, "Unwanted aqua must return to the network");
            helper.assertTrue(lamp.charges() > 0, "The bus must go on to supply the lamp's LIFE");
            helper.succeed();
        });
    }

    /** TEG-01: a golem that dies drops its backpack. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deadGolemDropsBackpack(GameTestHelper helper) {
        var pos = new BlockPos(1, 2, 1);
        ThaumcraftGolemEntity golem = helper.spawn(TCEntityTypes.GOLEM.get(), pos);
        GolemBackpackHandler.setBackpackLink(golem,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO)));
        golem.kill();
        helper.runAfterDelay(5, () -> {
            helper.assertItemEntityPresent(ModItems.GOLEM_WIFI_BACKPACK.get(), pos, 3.0);
            helper.succeed();
        });
    }

    private static boolean recipesRegistered;

    private static synchronized void registerRecipes() {
        if (recipesRegistered) return;
        recipesRegistered = true;
        ThaumcraftApi.getCraftingRecipes().add(new ShapelessArcaneRecipe("", new ItemStack(Items.TUBE_CORAL),
                new AspectList().add(Aspect.AIR, 2), Items.DEAD_BUSH, Items.FLINT));
        ThaumcraftApi.getCraftingRecipes().add(new ShapelessArcaneRecipe("", new ItemStack(Items.SPONGE),
                new AspectList().add(Aspect.AIR, 2), Items.WATER_BUCKET, Items.DEAD_BUSH));
    }

    private static ArcaneCraftingTerminalPart terminalPart(GameTestHelper helper) {
        var cablePos = new BlockPos(1, 1, 1);
        helper.setBlock(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var absolute = helper.absolutePos(cablePos);
        PartHelper.setPart(helper.getLevel(), absolute, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        return PartHelper.setPart(helper.getLevel(), absolute, Direction.UP, null,
                ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get());
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name),
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    private static ArcaneCraftingResultSlot resultSlot(ContainerArcaneCraftingTerminal menu) {
        for (var slot : menu.slots) {
            if (slot instanceof ArcaneCraftingResultSlot result) return result;
        }
        throw new IllegalStateException("No arcane result slot");
    }

    private static ItemStack chargedWand() {
        var item = BuiltInRegistries.ITEM.stream()
                .filter(i -> i instanceof TCFunctionalItems.WandCastingItem).findFirst().orElseThrow();
        var wand = new ItemStack(item);
        var casting = (TCFunctionalItems.WandCastingItem) item;
        for (var aspect : new Aspect[]{Aspect.AIR, Aspect.WATER, Aspect.FIRE, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY}) {
            casting.storeVis(wand, aspect, Integer.MAX_VALUE);
        }
        return wand;
    }

    private static ItemStack storedWand(ItemStack terminal) {
        return terminal.get(ModComponents.WAND_INV.get()).getStackInSlot(0);
    }

    private static int vis(ItemStack wand, Aspect aspect) {
        return ((TCFunctionalItems.WandCastingItem) wand.getItem()).getVis(wand, aspect);
    }

    private static AEssentiaKey key(Aspect aspect) {
        return AEssentiaKey.of(AspectIds.getAspectId(aspect));
    }

    private static Block tc(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
    }
}
