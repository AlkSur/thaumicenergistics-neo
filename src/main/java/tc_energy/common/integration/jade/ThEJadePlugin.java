package tc_energy.common.integration.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import tc_energy.common.tiles.TileArcaneAssembler;
import tc_energy.common.tiles.TileEssentiaProvider;
import tc_energy.common.tiles.TileEssentiaVibrationChamber;
import tc_energy.common.tiles.TileInfusionMonitor;
import tc_energy.common.tiles.TileInfusionProvider;
import tc_energy.init.ModBlocks;

@WailaPlugin
public class ThEJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(VibrationChamberProvider.INSTANCE, TileEssentiaVibrationChamber.class);
        registration.registerBlockDataProvider(InfusionMonitorProvider.INSTANCE, TileInfusionMonitor.class);
        // 注魔供应器 + 源质供应器共用网络状态显示
        registration.registerBlockDataProvider(InfusionProviderProvider.INSTANCE, TileInfusionProvider.class);
        registration.registerBlockDataProvider(InfusionProviderProvider.INSTANCE, TileEssentiaProvider.class);
        // 奥术装配器：网络状态 + 要素源状态
        registration.registerBlockDataProvider(AssemblerProvider.INSTANCE, TileArcaneAssembler.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        Block chamberBlock = ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get();
        registration.registerBlockComponent(VibrationChamberProvider.INSTANCE, chamberBlock.getClass());

        Block monitorBlock = ModBlocks.INFUSION_MONITOR.get();
        registration.registerBlockComponent(InfusionMonitorProvider.INSTANCE, monitorBlock.getClass());

        // 注魔供应器 + 源质供应器共用网络状态显示
        Block infusionProviderBlock = ModBlocks.INFUSION_PROVIDER.get();
        registration.registerBlockComponent(InfusionProviderProvider.INSTANCE, infusionProviderBlock.getClass());
        Block essentiaProviderBlock = ModBlocks.ESSENTIA_PROVIDER.get();
        registration.registerBlockComponent(InfusionProviderProvider.INSTANCE, essentiaProviderBlock.getClass());

        Block assemblerBlock = ModBlocks.ARCANE_ASSEMBLER.get();
        registration.registerBlockComponent(AssemblerProvider.INSTANCE, assemblerBlock.getClass());
    }
}
