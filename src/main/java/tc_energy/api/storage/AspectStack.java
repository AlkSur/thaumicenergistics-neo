package tc_energy.api.storage;
import net.minecraft.resources.ResourceLocation;

public record AspectStack(ResourceLocation aspectId, long amount) implements IAspectStack {
    public static final AspectStack EMPTY = new AspectStack(ResourceLocation.fromNamespaceAndPath("tc_energy", "empty"), 0);
    @Override public boolean isEmpty() { return amount <= 0; }
    @Override public IAspectStack copy(long newAmount) { return new AspectStack(aspectId, newAmount); }
}
