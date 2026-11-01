package thaumicenergistics.config;
public final class ThEConfig {
    private ThEConfig() {}
    public static final double EVC_POWER_PER_TICK = 5.0;
    public static final int CELL_1K = 1024, CELL_4K = 4096, CELL_16K = 16384, CELL_64K = 65536;
    /** Type limits for 1k/4k/16k, 64k, 256k, and the megabyte tiers respectively. */
    public static final int CELL_MAX_TYPES = 12;
    public static final int CELL_64K_MAX_TYPES = 15;
    public static final int CELL_256K_MAX_TYPES = 31;
    public static final int CELL_MEGABYTE_MAX_TYPES = 63;
}
