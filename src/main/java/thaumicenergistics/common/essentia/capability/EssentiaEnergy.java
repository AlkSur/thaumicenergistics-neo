package thaumicenergistics.common.essentia.capability;

import java.util.function.LongUnaryOperator;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;

public final class EssentiaEnergy {
    private EssentiaEnergy() {
    }

    public static long transfer(IGrid grid, long requested, LongUnaryOperator operation) {
        var energy = grid.getEnergyService();
        return TransferBudget.transfer(requested, EssentiaConversionHelper.AE_PER_ESSENTIA,
                cost -> energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG),
                unused -> energy.injectPower(PowerMultiplier.CONFIG.multiply(unused), Actionable.MODULATE),
                operation);
    }
}
