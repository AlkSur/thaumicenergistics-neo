package thaumicenergistics.tests;

import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import de.mari_023.ae2wtlib.api.AE2wtlibAPI;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import thaumicenergistics.common.integration.ae2wt.SafeTerminalMerge;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModComponents;

/** 可选 API 测试与通用测试类隔离，缺席时不解析其类型。 */
final class WirelessMergeChecks {
    static void run(GameTestHelper helper) {
        var arcane = new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        var crafting = new ItemStack(appeng.core.definitions.AEItems.WIRELESS_CRAFTING_TERMINAL.asItem());
        helper.assertTrue(crafting.getItem() instanceof ItemWT, "Pinned AE2WT crafting terminal must exist");
        arcane.set(ModComponents.WAND_INV.get(), ItemContainerContents.fromItems(List.of(new ItemStack(Items.STICK))));
        arcane.set(AEComponents.CRAFTING_INV, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE, 3))));
        ((ItemWT) arcane.getItem()).injectAEPower(arcane, 100, Actionable.MODULATE);
        ((ItemWT) crafting.getItem()).injectAEPower(crafting, 200, Actionable.MODULATE);
        var merged = SafeTerminalMerge.merge(arcane, crafting, "crafting");
        helper.assertFalse(merged.isEmpty(), "Normal arcane + crafting merge must remain craftable");
        helper.assertTrue(AE2wtlibAPI.isUniversalTerminal(merged), "Merge result must be a universal terminal");
        helper.assertTrue(merged.has(WTDefinition.of(arcane).componentType()), "Arcane mode flag must be preserved");
        helper.assertTrue(((ItemWT) merged.getItem()).getAECurrentPower(merged) == 300, "Both batteries migrate");
        helper.assertTrue(merged.get(ModComponents.WAND_INV.get()).getStackInSlot(0).is(Items.STICK), "Wand inventory migrates");
        helper.assertTrue(merged.get(AEComponents.CRAFTING_INV).getStackInSlot(0).getCount() == 3, "Crafting materials migrate");
        helper.assertTrue(SafeTerminalMerge.merge(arcane, merged, "").isEmpty(), "Duplicate arcane mode must be rejected");
        helper.assertTrue(((ItemWT) arcane.getItem()).getAECurrentPower(arcane) == 100, "Recipe preview cannot debit input battery");
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "UniversalChargeTest"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var craftingMode = WTDefinition.of(crafting);
        merged.set(de.mari_023.ae2wtlib.api.AE2wtlibComponents.CURRENT_TERMINAL, craftingMode);
        player.getInventory().setItem(0, merged);
        var passiveHost = thaumicenergistics.common.integration.ae2wt.AE2WTCompat.createPassiveArcaneHost(
                player, appeng.menu.locator.MenuLocators.forInventorySlot(0));
        helper.assertTrue(passiveHost instanceof thaumicenergistics.common.container.IArcaneTerminalInventoryHost,
                "Universal terminal in crafting mode must expose its passive arcane inventory");
        helper.assertTrue(merged.get(de.mari_023.ae2wtlib.api.AE2wtlibComponents.CURRENT_TERMINAL) == craftingMode,
                "Passive arcane host creation must not change the selected terminal");
        helper.assertTrue(merged.get(ModComponents.WAND_INV.get()).getStackInSlot(0).is(Items.STICK),
                "Passive host must preserve the saved wand");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.discard();
    }
}
