package thaumicenergistics.common.integration.appeng;

import java.util.ArrayList;
import java.util.List;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.tc.AspectIds;

/**
 * The AE keys of every registered aspect. Looking each one up in AE2's cached inventory costs
 * O(aspects); walking a network's whole inventory costs O(every item type) (TEC-02, TEM-04).
 */
public final class EssentiaKeys {
    private static volatile List<AEssentiaKey> keys = List.of();
    private static volatile int builtFrom = -1;

    private EssentiaKeys() {
    }

    public static List<AEssentiaKey> registered() {
        var aspects = Aspect.aspects;
        if (aspects == null) return List.of();
        int size = aspects.size();
        if (size != builtFrom) {
            synchronized (EssentiaKeys.class) {
                if (size != builtFrom) {
                    var built = new ArrayList<AEssentiaKey>(size);
                    for (Aspect aspect : aspects.values()) {
                        var id = AspectIds.getAspectId(aspect);
                        if (id != null) built.add(AEssentiaKey.of(id));
                    }
                    keys = List.copyOf(built);
                    builtFrom = size;
                }
            }
        }
        return keys;
    }
}
