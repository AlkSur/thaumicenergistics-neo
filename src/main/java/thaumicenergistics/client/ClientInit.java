package thaumicenergistics.client;

import appeng.api.client.AEKeyRendering;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;

public final class ClientInit {
    private ClientInit() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(EssentiaTerminalInputHandler.class);
        event.enqueueWork(() -> {
            AEKeyRendering.register(
                    AEssentiaKeyType.INSTANCE,
                    AEssentiaKey.class,
                    new EssentiaKeyRenderHandler()
            );
            ThaumicEnergistics.LOG.info("EssentiaKeyRenderHandler registered successfully!");
        });
    }
}
