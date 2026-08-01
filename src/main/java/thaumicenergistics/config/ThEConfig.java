package thaumicenergistics.config;
import java.util.Set;
public final class ThEConfig {
    private ThEConfig() {}
    public static final double EVC_POWER_PER_TICK = 5.0;
    public static final int CELL_1K = 1024, CELL_4K = 4096, CELL_16K = 16384, CELL_64K = 65536;
    public static final int CELL_MAX_TYPES = 12;
    private static final Set<String> ENABLED = Set.of("cells","io_buses","monitoring","arcane_crafting_terminal","essentia_provider","infusion_provider","vibration_chamber","vis_interface","golem_backpack");
    public static boolean isFeatureEnabled(String n) { return ENABLED.contains(n); }
}
