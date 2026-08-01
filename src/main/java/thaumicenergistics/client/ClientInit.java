package thaumicenergistics.client;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;

public final class ClientInit {
    private ClientInit() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            appeng.api.client.AEKeyRendering.register(
                    AEssentiaKeyType.INSTANCE,
                    AEssentiaKey.class,
                    new EssentiaKeyRenderHandler()
            );
            ThaumicEnergistics.LOG.info("EssentiaKeyRenderHandler registered successfully!");
        });
    }
}