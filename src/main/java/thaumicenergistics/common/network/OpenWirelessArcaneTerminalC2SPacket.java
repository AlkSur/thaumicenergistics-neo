package thaumicenergistics.common.network;

import appeng.integration.modules.curios.CuriosIntegration;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.ae2wt.AE2WTCompat;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalItem;

/** Finds the actual carried/equipped terminal; no client-supplied stack or slot is trusted. */
public record OpenWirelessArcaneTerminalC2SPacket() implements CustomPacketPayload {
    public static final Type<OpenWirelessArcaneTerminalC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("open_wireless_arcane_terminal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWirelessArcaneTerminalC2SPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenWirelessArcaneTerminalC2SPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handleOnServer(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) return;
            // Do not replace another open container or reopen an already open terminal.
            if (player.containerMenu != player.inventoryMenu) return;
            boolean hasWirelessLibrary = ModList.get().isLoaded("ae2wtlib");
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (tryOpen(player, MenuLocators.forInventorySlot(slot), hasWirelessLibrary)) return;
            }
            // AE2 exposes the capability even without Curios; absent providers return null.
            var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
            if (curios != null) {
                for (int slot = 0; slot < curios.getSlots(); slot++) {
                    if (tryOpen(player, MenuLocators.forCurioSlot(slot), hasWirelessLibrary)) return;
                }
            }
        });
    }

    private static boolean tryOpen(ServerPlayer player, ItemMenuHostLocator locator, boolean hasWirelessLibrary) {
        var stack = locator.locateItem(player);
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof WirelessArcaneCraftingTerminalItem terminal) {
            return terminal.openFromInventory(player, locator);
        }
        return hasWirelessLibrary
                && AE2WTCompat.openArcaneFromInventory(player, locator);
    }
}
