package thaumicenergistics.tests;

import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumicenergistics.common.container.ContainerArcaneAssembler;
import thaumicenergistics.common.container.slot.GhostSlot;
import thaumicenergistics.common.tiles.TileArcaneAssembler;
import thaumicenergistics.common.tiles.TileDistillationPatternEncoder;
import thaumicenergistics.init.ModBlocks;

/** Regression cover for the earlier partial audit's fixes TEA-01..04, which shipped without tests. */
@GameTestHolder("thaumicenergistics_tests")
@PrefixGameTestTemplate(false)
public final class TeaRegressionChecks {
    private TeaRegressionChecks() { }

    /** TEA-01: ghost template slots are never picked up (double-click collection duplicated them). */
    @GameTest(template = "empty")
    public static void ghostSlotsCannotBePickedUp(GameTestHelper helper) {
        var player = player(helper);
        var slot = new GhostSlot(new SimpleContainer(1), 0, 0, 0);
        slot.set(new ItemStack(Items.DIAMOND));
        helper.assertFalse(slot.mayPickup(player), "Ghost copies must not be taken");
        player.discard();
        helper.succeed();
    }

    /** TEA-03: the assembler menu closes with its block, and breaking empties the dropped slots. */
    @GameTest(template = "empty")
    public static void assemblerMenuClosesAndBreakEmptiesSlots(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ARCANE_ASSEMBLER.get());
        TileArcaneAssembler tile = helper.getBlockEntity(pos);
        var player = player(helper);
        var abs = helper.absolutePos(pos);
        player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 2.5);
        var menu = new ContainerArcaneAssembler(21, player.getInventory(), tile);
        helper.assertTrue(menu.stillValid(player), "Menu is valid next to its block");
        tile.getInternalInventory().setItem(TileArcaneAssembler.UPGRADE_SLOT_START, new ItemStack(AEItems.SPEED_CARD));
        tile.onBreakBlock();
        helper.assertTrue(tile.getInternalInventory().getItem(TileArcaneAssembler.UPGRADE_SLOT_START).isEmpty(),
                "Dropped items must leave the slots, or an open menu takes them a second time");
        helper.setBlock(pos, Blocks.AIR);
        helper.assertFalse(menu.stillValid(player), "Menu must close once the block is gone");
        player.discard();
        helper.succeed();
    }

    /** TEA-04: breaking the distillation encoder drops its patterns instead of deleting them. */
    @GameTest(template = "empty")
    public static void distillationEncoderDropsPatterns(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.DISTILLATION_ENCODER.get());
        TileDistillationPatternEncoder tile = helper.getBlockEntity(pos);
        tile.inventory.setStackInSlot(TileDistillationPatternEncoder.SLOT_BLANK_PATTERNS,
                AEItems.BLANK_PATTERN.stack(5));
        helper.destroyBlock(pos);
        helper.assertItemEntityPresent(AEItems.BLANK_PATTERN.asItem(), pos, 2.0);
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "TeaRegression"),
                net.minecraft.server.level.ClientInformation.createDefault());
    }
}
