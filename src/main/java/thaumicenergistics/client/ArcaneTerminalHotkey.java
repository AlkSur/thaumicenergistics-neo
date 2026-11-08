package thaumicenergistics.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.network.OpenWirelessArcaneTerminalC2SPacket;

@EventBusSubscriber(modid = ThaumicEnergistics.MODID, value = Dist.CLIENT)
public final class ArcaneTerminalHotkey {
    private static final KeyMapping OPEN = new KeyMapping("key.thaumicenergistics.open_wireless_arcane_terminal",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.thaumicenergistics");

    private ArcaneTerminalHotkey() { }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        boolean pressed = false;
        while (OPEN.consumeClick()) pressed = true;
        var minecraft = Minecraft.getInstance();
        if (pressed && minecraft.player != null && minecraft.screen == null)
            PacketDistributor.sendToServer(new OpenWirelessArcaneTerminalC2SPacket());
    }
}
