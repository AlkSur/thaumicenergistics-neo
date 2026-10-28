package thaumicenergistics.common.essentia.capability;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongUnaryOperator;

/** 先取得能量预算再搬运，部分接收只结算实际量，退回未使用的预算。 */
public final class TransferBudget {
    private TransferBudget() {
    }

    public static long transfer(long requested, double unitCost, DoubleUnaryOperator reserve,
            DoubleConsumer refund, LongUnaryOperator operation) {
        if (requested <= 0 || !Double.isFinite(unitCost) || unitCost <= 0) {
            return 0;
        }
        double reserved = reserve.applyAsDouble(requested * unitCost);
        long allowed = Math.min(requested, (long) Math.floor((reserved + 1.0e-9) / unitCost));
        long moved = 0;
        try {
            if (allowed > 0) {
                long transferred = operation.applyAsLong(allowed);
                if (transferred < 0 || transferred > allowed) {
                    throw new IllegalStateException("Transfer exceeded its reserved resource budget");
                }
                moved = transferred;
            }
            return moved;
        } finally {
            double unused = reserved - moved * unitCost;
            if (unused > 1.0e-9) {
                refund.accept(unused);
            }
        }
    }
}
