package thaumicenergistics.common.integration.appeng;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.common.integration.tc.EssentiaEndpoints;
import thaumicenergistics.init.ModItems;

/** 所有供应器共用已持有资源的输出目标；可选兼容层仅提供宿主和访问面。 */
public final class ProviderEssentiaTargets {
    private ProviderEssentiaTargets() { }

    public static boolean hasCard(Object host) {
        return host instanceof IUpgradeableObject upgradeable
                && upgradeable.getUpgrades().isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }

    @Nullable
    public static PatternProviderTarget wrap(@Nullable PatternProviderTarget original, @Nullable IGrid grid,
            IActionSource source, @Nullable BlockEntity target, @Nullable Direction face) {
        if (grid == null || target == null || !(target instanceof IAspectContainer)
                && EssentiaEndpoints.transport(target, face) == null) return original;
        if (original instanceof ThEEssentiaPushTarget || original instanceof ThECompositePushTarget) return original;
        var essentia = new ThEEssentiaPushTarget(grid, source, target, face);
        return original == null ? essentia : new ThECompositePushTarget(original, essentia);
    }

    @Nullable
    public static PatternProviderTarget fromSource(@Nullable PatternProviderTarget original, IActionSource source,
            @Nullable BlockEntity target, @Nullable Direction face) {
        var machine = source.machine().orElse(null);
        if (!hasCard(machine)) return original;
        var node = machine.getActionableNode();
        return wrap(original, node != null ? node.getGrid() : null, source, target, face);
    }
}
