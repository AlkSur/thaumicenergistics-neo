package thaumicenergistics.api.storage;
import net.minecraft.resources.ResourceLocation;

public interface IAspectStack { ResourceLocation aspectId(); long amount(); boolean isEmpty(); IAspectStack copy(long newAmount); }
