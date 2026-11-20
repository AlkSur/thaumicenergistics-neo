package thaumicenergistics.api.essentia;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Registry of {@link IEssentiaEndpointProvider}s. Register during common setup; lookups are lock-free. */
public final class EssentiaEndpointProviders {
    private static final List<IEssentiaEndpointProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private EssentiaEndpointProviders() {
    }

    public static void register(IEssentiaEndpointProvider provider) {
        PROVIDERS.add(java.util.Objects.requireNonNull(provider));
    }

    /** The provider that owns {@code blockEntity}, or null to let Thaumic Energistics handle it itself. */
    @Nullable
    public static IEssentiaEndpointProvider find(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || PROVIDERS.isEmpty()) return null;
        for (var provider : PROVIDERS) {
            if (provider.owns(blockEntity)) return provider;
        }
        return null;
    }
}
