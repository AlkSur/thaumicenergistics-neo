package thaumicenergistics.common.integration.neoecoae;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageChannelDisplays;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Essentia storage matrices for Neo ECO AE drives (E3/E4), built only on ECO's public storage API: our own cell
 * handler and cell inventory, our cell type in ECO's cell type registry, and a channel display for the storage
 * host's statistics. Loaded only through {@link NeoEcoItems} after it has checked that the API exists.
 */
public final class ThENeoEcoStorage {
    private ThENeoEcoStorage() { }

    /** Essentia's colour in ECO's storage statistics. */
    public static final int COLOR = 0xB07CFF;

    private static final ResourceKey<Registry<ECOCellType>> CELL_TYPE_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoecoae", "cell_type"));
    private static final DeferredRegister<ECOCellType> CELL_TYPES =
            DeferredRegister.create(CELL_TYPE_REGISTRY, ThaumicEnergistics.MODID);
    public static final DeferredHolder<ECOCellType, ECOCellType> ESSENTIA = CELL_TYPES.register("essentia",
            () -> new ECOCellType(essentiaName(), EssentiaMatrixItem.TYPES));

    static void registerCellTypes(IEventBus modBus) {
        CELL_TYPES.register(modBus);
    }

    static Item matrix(String size) {
        return switch (size) {
            case "16m" -> new EssentiaMatrixItem(new Item.Properties().rarity(Rarity.UNCOMMON), ECOTier.L4, size);
            case "64m" -> new EssentiaMatrixItem(new Item.Properties().rarity(Rarity.RARE), ECOTier.L6, size);
            case "256m" -> new EssentiaMatrixItem(new Item.Properties().rarity(Rarity.EPIC), ECOTier.L9, size);
            default -> throw new IllegalArgumentException(size);
        };
    }

    static void setup() {
        ECOStorageCells.register(EssentiaMatrixCell.HANDLER);
        try {
            var channel = ESSENTIA.getId();
            ECOStorageChannelDisplays.register(channel, 0,
                    new ECOStorageChannelDisplays.Display(channel, Set.of(), essentiaName(), COLOR, null, null, 50));
        } catch (LinkageError older) {
            ThaumicEnergistics.LOG.info("Neo ECOAE has no storage channel displays: essentia uses the default line");
        }
        ThaumicEnergistics.LOG.info("Neo ECOAE essentia storage matrices enabled");
    }

    private static Component essentiaName() {
        return Component.translatable("ae2.keytype.thaumicenergistics.essentia").withColor(COLOR);
    }
}
