package thaumicenergistics.common.features;

import thaumicenergistics.ThaumicEnergistics;

public final class FeatureRegistry {
    private FeatureRegistry() {}
    private static final ThEFeatureBase[] FEATURES = {
        new FeatureCells(), new FeatureEssentiaIOBuses(), new FeatureEssentiaMonitoring(),
        new FeatureEssentiaVibrationChamber(), new FeatureInfusionProvider(),
        new FeatureAutocraftingArcane(), new FeatureVisRelayInterface(),
        new FeatureGolemBackpack(), new FeatureEssentiaProvider()
    };
    public static void registerAll() {
        for (var f : FEATURES) { ThaumicEnergistics.LOG.info("Feature [{}] {}", f.getName(), f.isEnabled() ? "enabled":"disabled"); f.register(); }
    }
    public static void postInitAll() { for (var f : FEATURES) f.postInit(); }
}
