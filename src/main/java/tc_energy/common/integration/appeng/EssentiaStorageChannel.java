package tc_energy.common.integration.appeng;

import net.minecraft.resources.ResourceLocation;
import tc_energy.api.storage.AspectStack;
import tc_energy.api.storage.IAspectStack;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

public final class EssentiaStorageChannel {
    private EssentiaStorageChannel() {}

    public static GenericStack toGenericStack(IAspectStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        AEssentiaKey key = AEssentiaKey.of(stack.aspectId());
        return new GenericStack(key, stack.amount());
    }

    public static IAspectStack fromGenericStack(Object obj) {
        if (obj instanceof GenericStack gs) {
            AEKey what = gs.what();
            if (what instanceof AEssentiaKey essKey) {
                return new AspectStack(essKey.getId(), gs.amount());
            }
        }
        return AspectStack.EMPTY;
    }

    public static AEssentiaKey toKey(ResourceLocation aspectId) {
        return AEssentiaKey.of(aspectId);
    }
}