package thaumicenergistics.api.essentia;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.api.storage.AspectStack;

/**
 * Lets an addon tell Thaumic Energistics' buses, storage buses and providers how to read and move essentia in its
 * own block entities, instead of patching TE's internals with a mixin (TEL-07). Register with
 * {@link EssentiaEndpointProviders#register}. The first provider that {@link #owns} a block entity answers for it.
 * Aspect ids are Thaumcraft ids such as {@code thaumcraft:aer}; amounts are whole essentia.
 */
public interface IEssentiaEndpointProvider {
    /** Whether this provider answers for {@code blockEntity}. Called often: keep it cheap. */
    boolean owns(BlockEntity blockEntity);

    /** The essentia that may be taken through {@code side} (null: any side), without changing anything. */
    List<AspectStack> contents(BlockEntity blockEntity, @Nullable Direction side);

    /** Takes up to {@code amount} of {@code aspectId} through {@code side}; null or empty when nothing was taken. */
    @Nullable
    AspectStack extract(BlockEntity blockEntity, ResourceLocation aspectId, int amount, @Nullable Direction side);

    /** Puts up to {@code amount} of {@code aspectId} in through {@code side}; returns how much was accepted. */
    int insert(BlockEntity blockEntity, ResourceLocation aspectId, int amount, @Nullable Direction side);
}
