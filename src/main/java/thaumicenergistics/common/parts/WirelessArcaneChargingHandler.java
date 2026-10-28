package thaumicenergistics.common.parts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.integration.modules.curios.CuriosIntegration;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.MenuLocators;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.IArcaneTerminalInventoryHost;
import thaumicenergistics.common.integration.ae2wt.AE2WTCompat;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalItem;
import thaumicenergistics.init.ModComponents;
import thaumicenergistics.init.ModItems;

/** One server-side charging path for closed terminals and every universal-terminal mode. */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class WirelessArcaneChargingHandler {
    // Hosts retain native AE2's ten-tick idle-energy buffer. Recreating one every tick would overcharge power.
    private static final Map<UUID, Map<Integer, PassiveHost>> HOSTS = new HashMap<>();
    private record PassiveHost(ServerPlayer player, ItemStack stack, ItemContainerContents wands,
                               WirelessTerminalMenuHost<?> host) { }
    private WirelessArcaneChargingHandler() { }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            replenishCarriedTerminal(player, slot);
        }
        // AE2 exposes this capability without a Curios runtime dependency; absent mods return null.
        var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        int curioSlots = curios == null ? 0 : curios.getSlots();
        for (int slot = 0; slot < curioSlots; slot++) {
            replenishCarriedTerminal(player, -slot - 1, MenuLocators.forCurioSlot(slot));
        }
        var hosts = HOSTS.get(player.getUUID());
        if (hosts != null) {
            hosts.keySet().removeIf(key -> key < 0 && -key - 1 >= curioSlots);
            if (hosts.isEmpty()) HOSTS.remove(player.getUUID());
        }
    }

    public static void replenishCarriedTerminal(ServerPlayer player, int slot) {
        replenishCarriedTerminal(player, slot, MenuLocators.forInventorySlot(slot));
    }

    private static void replenishCarriedTerminal(ServerPlayer player, int slot, ItemMenuHostLocator locator) {
        ItemStack stack = locator.locateItem(player);
        var hosts = HOSTS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        var openHost = player.containerMenu instanceof AEBaseMenu menu
                && menu.getTarget() instanceof WirelessTerminalMenuHost<?> terminal
                && terminal.getItemStack() == stack ? terminal : null;

        if (!(stack.getItem() instanceof WirelessTerminalItem item)
                || !item.getUpgrades(stack).isInstalled(ModItems.VIS_CONNECTOR_CARD.get())) {
            hosts.remove(slot);
            return;
        }
        // Use the live wand inventory while the arcane menu is open; never overwrite it with a saved copy.
        if (openHost instanceof IArcaneTerminalInventoryHost arcane) {
            hosts.remove(slot);
            if (openHost.getLinkStatus().connected() && WirelessArcaneVis.replenish(arcane)) {
                player.getInventory().setChanged();
            }
            return;
        }
        var wands = stack.getOrDefault(ModComponents.WAND_INV.get(), ItemContainerContents.EMPTY);
        if (wands.getSlots() == 0 || !ArcaneVisReplenish.needsCharge(wands.getStackInSlot(0))) {
            hosts.remove(slot);
            return;
        }
        var cached = hosts.get(slot);
        WirelessTerminalMenuHost<?> host;
        if (cached != null && cached.player() == player && cached.stack() == stack
                && cached.wands().equals(wands)) {
            host = cached.host();
        } else {
            if (item instanceof WirelessArcaneCraftingTerminalItem arcane) {
                host = arcane.getMenuHost(player, locator, null);
            } else if (ModList.get().isLoaded(IArcaneTerminalInventoryHost.AE2WTLIB_MODID)) {
                host = AE2WTCompat.createPassiveArcaneHost(player, locator);
            } else {
                host = null;
            }
            if (host == null) {
                hosts.remove(slot);
                return;
            }
            hosts.put(slot, new PassiveHost(player, stack, wands, host));
        }
        if (!(host instanceof IArcaneTerminalInventoryHost arcane)) return;
        if (openHost == null) {
            // Refresh range/quantum connection and let the native host own terminal power accounting.
            host.tick();
        } else {
            // A different universal-terminal mode already ticks its host. Do not debit its idle power twice.
            arcane.refreshConnection();
        }
        if (host.getLinkStatus().connected() && WirelessArcaneVis.replenish(arcane)) {
            player.getInventory().setChanged();
        }
        // Detect external wand replacement while retaining the host for our own persisted charge updates.
        hosts.put(slot, new PassiveHost(player, stack,
                stack.getOrDefault(ModComponents.WAND_INV.get(), ItemContainerContents.EMPTY), host));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HOSTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOSTS.clear();
    }
}
