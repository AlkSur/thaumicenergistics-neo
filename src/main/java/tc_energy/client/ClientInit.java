package tc_energy.client;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.appeng.AEssentiaKeyType;

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