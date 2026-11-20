package thaumicenergistics.common.integration.neoecoae;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Neo ECOAE big orders reach AE2's submitJob as an empty carrier plan and plan their segments inside the ECO CPU
 * later, so TE's research gate saw no patterns (TEN-02). This exposes what such an order will craft: its goal and
 * every key in its preview. Loaded only when Neo ECOAE is installed.
 */
public final class ThENeoEcoOrders {
    private ThENeoEcoOrders() {
    }

    /** The keys a Neo ECOAE big order will craft, or an empty set when {@code plan} is not one. */
    public static Set<AEKey> bigOrderKeys(ICraftingPlan plan) {
        var request = ECOBigOrderRequest.forSubmission(plan);
        if (request == null) return Set.of();
        var keys = new LinkedHashSet<AEKey>();
        keys.add(request.goal());
        try {
            keys.addAll(request.pendingPreview().keySet());
        } catch (NoSuchMethodError older) {
            // Versions without a preview still name the goal.
        }
        return keys;
    }
}
