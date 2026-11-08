package thaumicenergistics.common.integration.appeng;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.api.storage.IAspectStack;

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