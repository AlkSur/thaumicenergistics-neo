package thaumicenergistics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration (config/thaumicenergistics-server.toml in each world's serverconfig, synced to clients),
 * so packs can tune numbers without editing the jar (TEU-06). Rules of play are not configurable here.
 * Every accessor falls back to the default until the config is loaded.
 */
public final class ThEConfig {
    private ThEConfig() {}

    /** AE2 type limits per cell tier; AE2 itself caps a cell at 63 types, so these stay fixed. */
    public static final int CELL_MAX_TYPES = 12;
    public static final int CELL_64K_MAX_TYPES = 15;
    public static final int CELL_256K_MAX_TYPES = 31;
    public static final int CELL_MEGABYTE_MAX_TYPES = 63;

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue CHAMBER_IGNIS_AE_PER_TICK;
    private static final ModConfigSpec.DoubleValue CHAMBER_POTENTIA_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue CHAMBER_OTHER_MULTIPLIER;
    private static final ModConfigSpec.IntValue CHAMBER_BURN_TICKS;
    private static final ModConfigSpec.IntValue CHAMBER_BUFFER;
    private static final ModConfigSpec.IntValue CHAMBER_MAX_OUTPUT;
    private static final ModConfigSpec.IntValue PROVIDER_MAX_RECEIVERS;
    private static final ModConfigSpec.IntValue PROVIDER_MAX_DISTANCE;
    private static final ModConfigSpec.IntValue ASSEMBLER_VIS_CACHE;
    private static final ModConfigSpec.DoubleValue ASSEMBLER_ACTIVE_POWER;
    private static final ModConfigSpec.IntValue ASSEMBLER_WARP_POWER_PERCENT;
    private static final ModConfigSpec.IntValue ASSEMBLER_PARALLEL_PER_CARD;
    private static final ModConfigSpec.IntValue ASSEMBLER_MAX_PARALLEL;
    private static final ModConfigSpec.IntValue CHAMBER_PARALLEL_L4;
    private static final ModConfigSpec.IntValue CHAMBER_PARALLEL_L6;
    private static final ModConfigSpec.IntValue CHAMBER_PARALLEL_L9;
    private static final ModConfigSpec.DoubleValue CELL_CAPACITY_MULTIPLIER;

    static {
        var b = new ModConfigSpec.Builder();
        b.comment("Essentia Vibration Chamber").push("vibration_chamber");
        CHAMBER_IGNIS_AE_PER_TICK = b.comment("AE/t while burning ignis; potentia and other aspects scale from this")
                .defineInRange("ignis_ae_per_tick", 200, 1, 100_000);
        CHAMBER_POTENTIA_MULTIPLIER = b.comment("Potentia power relative to ignis (its burn time is divided by the same factor)")
                .defineInRange("potentia_multiplier", 1.6, 0.1, 100.0);
        CHAMBER_OTHER_MULTIPLIER = b.comment("Power of any other aspect relative to ignis")
                .defineInRange("other_multiplier", 0.5, 0.0, 100.0);
        CHAMBER_BURN_TICKS = b.comment("Base burn time of one essentia, in ticks (ignis and others burn half of it)")
                .defineInRange("burn_ticks", 800, 20, 72_000);
        CHAMBER_BUFFER = b.comment("Internal energy buffer, AE").defineInRange("energy_buffer", 16_000, 1_000, 10_000_000);
        CHAMBER_MAX_OUTPUT = b.comment("Most AE pushed into the network per tick").defineInRange("max_output_per_tick", 2_000, 1, 1_000_000);
        b.pop();
        b.comment("Essentia Provider and wireless receivers").push("provider");
        PROVIDER_MAX_RECEIVERS = b.comment("Wireless receivers one provider can serve").defineInRange("max_receivers", 8, 1, 64);
        PROVIDER_MAX_DISTANCE = b.comment("Longest provider-receiver link, in blocks").defineInRange("max_link_distance", 32, 1, 256);
        b.pop();
        b.comment("Arcane Assembler").push("assembler");
        ASSEMBLER_VIS_CACHE = b.comment("Vis buffer per primal (grows to fit an order that needs more)")
                .defineInRange("vis_cache", 187, 10, 10_000);
        ASSEMBLER_ACTIVE_POWER = b.comment("AE/t while crafting, before speed cards and warp")
                .defineInRange("active_power", 1.5, 0.0, 10_000.0);
        ASSEMBLER_WARP_POWER_PERCENT = b.comment("Extra power use per point of warp on gear in its armor slots, percent")
                .defineInRange("warp_power_percent", 15, 0, 1_000);
        ASSEMBLER_PARALLEL_PER_CARD = b.comment("Extra parallel orders per acceleration card when driven by a Neo ECOAE crafting CPU")
                .defineInRange("parallel_per_speed_card", 2, 0, 64);
        ASSEMBLER_MAX_PARALLEL = b.comment("Most parallel orders one assembler takes from a Neo ECOAE crafting CPU")
                .defineInRange("max_parallel", 9, 1, 256);
        b.pop();
        b.comment("Arcane Crafting Chamber (Neo ECO AE linkage)").push("arcane_crafting_chamber");
        CHAMBER_PARALLEL_L4 = b.comment("Crafts one L4 chamber takes in a batch").defineInRange("parallel_l4", 32, 1, 4096);
        CHAMBER_PARALLEL_L6 = b.comment("Crafts one L6 chamber takes in a batch").defineInRange("parallel_l6", 128, 1, 4096);
        CHAMBER_PARALLEL_L9 = b.comment("Crafts one L9 chamber takes in a batch").defineInRange("parallel_l9", 512, 1, 4096);
        b.pop();
        b.comment("ME Essentia Storage Cells").push("cells");
        CELL_CAPACITY_MULTIPLIER = b.comment("Multiplies every essentia cell's byte capacity. Lowering it leaves fuller cells over capacity: they keep their essentia but take no more")
                .defineInRange("capacity_multiplier", 1.0, 0.01, 1_000.0);
        b.pop();
        SPEC = b.build();
    }

    private static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static double chamberIgnisAePerTick() { return loaded() ? CHAMBER_IGNIS_AE_PER_TICK.get() : 200; }
    public static double chamberPotentiaMultiplier() { return loaded() ? CHAMBER_POTENTIA_MULTIPLIER.get() : 1.6; }
    public static double chamberOtherMultiplier() { return loaded() ? CHAMBER_OTHER_MULTIPLIER.get() : 0.5; }
    public static int chamberBurnTicks() { return loaded() ? CHAMBER_BURN_TICKS.get() : 800; }
    public static double chamberBuffer() { return loaded() ? CHAMBER_BUFFER.get() : 16_000; }
    public static double chamberMaxOutput() { return loaded() ? CHAMBER_MAX_OUTPUT.get() : 2_000; }
    public static int providerMaxReceivers() { return loaded() ? PROVIDER_MAX_RECEIVERS.get() : 8; }
    public static int providerMaxDistance() { return loaded() ? PROVIDER_MAX_DISTANCE.get() : 32; }
    public static int assemblerVisCache() { return loaded() ? ASSEMBLER_VIS_CACHE.get() : 187; }
    public static double assemblerActivePower() { return loaded() ? ASSEMBLER_ACTIVE_POWER.get() : 1.5; }
    public static double assemblerWarpPowerFraction() { return (loaded() ? ASSEMBLER_WARP_POWER_PERCENT.get() : 15) / 100.0; }
    public static int assemblerParallelPerCard() { return loaded() ? ASSEMBLER_PARALLEL_PER_CARD.get() : 2; }
    public static int assemblerMaxParallel() { return loaded() ? ASSEMBLER_MAX_PARALLEL.get() : 9; }
    /** Batch size of an arcane crafting chamber of ECO tier 4, 6 or 9. */
    public static int chamberParallel(int tier) {
        if (!loaded()) return tier >= 9 ? 512 : tier >= 6 ? 128 : 32;
        return (tier >= 9 ? CHAMBER_PARALLEL_L9 : tier >= 6 ? CHAMBER_PARALLEL_L6 : CHAMBER_PARALLEL_L4).get();
    }
    public static double cellCapacityMultiplier() { return loaded() ? CELL_CAPACITY_MULTIPLIER.get() : 1.0; }
}
