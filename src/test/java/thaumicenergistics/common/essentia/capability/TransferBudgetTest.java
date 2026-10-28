package thaumicenergistics.common.essentia.capability;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TransferBudgetTest {
    private static final class Energy {
        double available;
        Energy(double available) { this.available = available; }
        double reserve(double requested) {
            double paid = Math.min(requested, available);
            available -= paid;
            return paid;
        }
        void refund(double amount) { available += amount; }
    }

    @Test
    void insufficientEnergyLimitsResourcesAndRefundsFraction() {
        var energy = new Energy(1.0);
        long moved = TransferBudget.transfer(8, 0.3, energy::reserve, energy::refund, allowed -> {
            assertEquals(3, allowed);
            return allowed;
        });
        assertEquals(3, moved);
        assertEquals(0.1, energy.available, 1e-9);
    }

    @Test
    void partialAcceptanceOnlyChargesActualResources() {
        var energy = new Energy(10);
        assertEquals(2, TransferBudget.transfer(8, 0.3, energy::reserve, energy::refund, allowed -> 2));
        assertEquals(9.4, energy.available, 1e-9);
    }

    @Test
    void rejectionAndExceptionRestoreUnusedBudget() {
        var energy = new Energy(10);
        assertEquals(0, TransferBudget.transfer(8, 0.3, energy::reserve, energy::refund, allowed -> 0));
        assertEquals(10, energy.available, 1e-9);
        assertThrows(IllegalStateException.class, () -> TransferBudget.transfer(8, 0.3,
                energy::reserve, energy::refund, allowed -> { throw new IllegalStateException("unavailable"); }));
        assertEquals(10, energy.available, 1e-9);
    }

    @Test
    void noWholeUnitDoesNotTouchResources() {
        var energy = new Energy(0.2);
        assertEquals(0, TransferBudget.transfer(8, 0.3, energy::reserve, energy::refund, allowed -> {
            fail("No whole unit can be paid for");
            return 0;
        }));
        assertEquals(0.2, energy.available, 1e-9);
    }

    @Test
    void exactDecimalBudgetDoesNotLoseOneUnitToRounding() {
        var energy = new Energy(0.3 * 7);
        assertEquals(7, TransferBudget.transfer(7, 0.3, energy::reserve, energy::refund, allowed -> allowed));
        assertEquals(0, energy.available, 1e-9);
    }

    @Test
    void invalidRequestsNeverReserveEnergy() {
        for (double cost : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, TransferBudget.transfer(8, cost,
                    amount -> { fail("Invalid cost must not debit energy"); return 0; },
                    amount -> fail("Nothing was reserved"), allowed -> { fail(); return 0; }));
        }
    }
}
