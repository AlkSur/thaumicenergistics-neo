package thaumicenergistics.common.integration.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;
import thaumicenergistics.init.ModBlocks;

@WailaPlugin
public class ThEJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(VibrationChamberProvider.INSTANCE, TileEssentiaVibrationChamber.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        Block chamberBlock = ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get();
        registration.registerBlockComponent(VibrationChamberProvider.INSTANCE, chamberBlock.getClass());
    }
}
