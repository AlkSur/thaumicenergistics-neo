package thaumicenergistics.tests;

import appeng.api.config.Actionable;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.common.blockentities.ArcaneLampFertilityBlockEntity;
import thaumcraft.common.blockentities.JarBlockEntity;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.essentia.capability.EssentiaContainerStorage;
import thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.common.integration.tc.EssentiaEndpoints;
import thaumicenergistics.init.ModItems;

final class EssentiaBusChecks {
    static void jarsFromEveryFace(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block("jar_of_essentia"));
        JarBlockEntity jar = helper.getBlockEntity(pos);
        var key = AEssentiaKey.of(AspectIds.getAspectId(Aspect.LIFE));
        var source = IActionSource.ofPlayer(helper.makeMockPlayer(GameType.SURVIVAL));
        for (Direction side : Direction.values()) {
            jar.clearContents();
            jar.removeFilter();
            var storage = new EssentiaContainerStorage(() -> jar, Component.literal("jar test"), () -> side);
            helper.assertTrue(storage.insert(key, 12, Actionable.SIMULATE, source) == 12,
                    "Storage simulation must accept jar face " + side);
            helper.assertTrue(jar.getAmount() == 0, "Simulation must not change the jar");
            helper.assertTrue(EssentiaEndpoints.insert(jar, key.getId(), 12, side) == 12,
                    "Export must fill jar face " + side);
            helper.assertTrue(EssentiaEndpoints.contents(jar, side).getFirst().amount() == 12,
                    "Import must see the same contents on " + side);
            helper.assertTrue(storage.extract(key, 4, Actionable.SIMULATE, source) == 4 && jar.getAmount() == 12,
                    "Storage extraction simulation is non-destructive");
            var extracted = EssentiaEndpoints.extract(jar, key.getId(), 4, side);
            helper.assertTrue(extracted != null && extracted.amount() == 4 && jar.getAmount() == 8,
                    "Import must extract from jar face " + side);
            helper.assertTrue(storage.insert(key, 100, Actionable.SIMULATE, source) == 56,
                    "Six-face access must retain the jar capacity");
            helper.assertTrue(storage.insert(key, 100, Actionable.MODULATE, source) == 56 && jar.getAmount() == 64,
                    "Storage simulation and actual insertion must agree");
            jar.clearContents();
            jar.setFilter(Aspect.AIR);
            helper.assertTrue(EssentiaEndpoints.insert(jar, key.getId(), 1, side) == 0
                    && storage.insert(key, 1, Actionable.SIMULATE, source) == 0,
                    "Six-face access must retain the label filter");
        }
        helper.succeed();
    }

    static void fertilityLampPullsFromExportBus(GameTestHelper helper) {
        var level = helper.getLevel();
        var cablePos = new BlockPos(1, 1, 1);
        var lampPos = cablePos.above();
        helper.setBlock(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var absoluteCable = helper.absolutePos(cablePos);
        PartHelper.setPart(level, absoluteCable, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(level, absoluteCable, Direction.UP, null, AEParts.EXPORT_BUS.get());
        helper.assertTrue(bus != null, "Export bus must install below the lamp");
        var key = AEssentiaKey.of(AspectIds.getAspectId(Aspect.LIFE));
        bus.getConfig().setStack(0, new GenericStack(key, 1));
        helper.setBlock(lampPos, block("arcane_lamp_fertility"));
        ArcaneLampFertilityBlockEntity lamp = helper.getBlockEntity(lampPos);
        var nbt = lamp.saveWithFullMetadata(level.registryAccess());
        nbt.putInt("orientation", Direction.DOWN.get3DDataValue());
        lamp.loadWithComponents(nbt, level.registryAccess());
        long[] remaining = {16};
        MEStorage inventory = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("lamp supply"); }
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                long taken = key.equals(what) ? Math.min(amount, remaining[0]) : 0;
                if (mode == Actionable.MODULATE) remaining[0] -= taken;
                return taken;
            }
            @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                if (!key.equals(what)) return 0;
                if (mode == Actionable.MODULATE) remaining[0] += amount;
                return amount;
            }
        };
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(bus.getMainNode().isActive(), "Test ME network must have power and a channel");
            var grid = bus.getMainNode().getGrid();
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(inventory, 0));
            var endpoint = EssentiaCapabilities.find(level, absoluteCable, Direction.UP);
            helper.assertTrue(endpoint != null && !endpoint.canOutputTo(Direction.UP), "No card must mean no supply");
            helper.assertTrue(bus.getUpgrades().addItems(new ItemStack(ModItems.ESSENTIA_UPGRADE_CARD.get())).isEmpty(),
                    "Essentia card must install");
            grid.getTickManager().alertDevice(bus.getMainNode().getNode());
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(lamp.charges() > 0, "Real fertility lamp must draw LIFE without an intermediary jar");
            var buffer = (EssentiaTransferBuffer) bus;
            helper.assertTrue(remaining[0] + buffer.thE$getTransferBuffer().amount() + lamp.charges() == 16,
                    "Network, persisted bus stock and lamp charges must conserve essentia");
            var endpoint = EssentiaCapabilities.find(level, absoluteCable, Direction.UP);
            helper.assertTrue(endpoint.takeEssentia(Aspect.AIR, 1, Direction.UP) == 0,
                    "Wrong aspect must not consume stock");
            helper.assertTrue(endpoint.takeEssentia(Aspect.LIFE, 1, Direction.DOWN) == 0,
                    "Only the outward export-bus face may supply");
            bus.getUpgrades().addItems(AEItems.REDSTONE_CARD.stack());
            bus.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL);
            helper.assertTrue(endpoint.takeEssentia(Aspect.LIFE, 1, Direction.UP) == 0,
                    "Disabled redstone mode must block consumer pulls");
            bus.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
            var saved = new net.minecraft.nbt.CompoundTag();
            bus.writeToNBT(saved, level.registryAccess());
            var stock = buffer.thE$getTransferBuffer();
            buffer.thE$setTransferBuffer(AspectStack.EMPTY);
            bus.readFromNBT(saved, level.registryAccess());
            helper.assertTrue(buffer.thE$getTransferBuffer().equals(stock), "Owned stock must survive bus reload");
            bus.getConfig().setStack(0, null);
            helper.assertTrue(!endpoint.canOutputTo(Direction.UP), "Removing filter must immediately block pulls");
            var host = (CableBusBlockEntity) helper.getBlockEntity(cablePos);
            host.removePartFromSide(Direction.UP);
            helper.assertTrue(!endpoint.canOutputTo(Direction.UP), "Cached endpoint must not outlive its bus");
            helper.succeed();
        });
    }

    private static net.minecraft.world.level.block.Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
    }
}
