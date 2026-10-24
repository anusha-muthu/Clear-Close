package com.clearclose.exit.preclosure;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreClosureSettlementCalculatorTest {
    private final LocalDate today = LocalDate.of(2026, 10, 24);

    private PreClosureSettlementCalculator.Terms terms(String rateType, String permission,
            String chargeType, BigDecimal fee, BigDecimal tax, LocalDate paidThrough) {
        return new PreClosureSettlementCalculator.Terms(new BigDecimal("25000"), new BigDecimal("12"),
                today.minusMonths(15), paidThrough, today, rateType, "NON_BUSINESS", permission,
                chargeType, fee, tax);
    }

    @Test void matchesRequestedExample() {
        var result = PreClosureSettlementCalculator.calculate(terms("FIXED", "Y", "PERCENTAGE",
                new BigDecimal("2"), new BigDecimal("18"), today.minusDays(10)));
        assertEquals(new BigDecimal("25000.00"), result.principal());
        assertEquals(new BigDecimal("82.19"), result.interest());
        assertEquals(new BigDecimal("500.00"), result.charges());
        assertEquals(new BigDecimal("90.00"), result.tax());
        assertEquals(new BigDecimal("25672.19"), result.total());
        assertEquals(10, result.interestDays());
    }

    @Test void protectedFloatingLoanOverridesProductFeeAndPermissionFlag() {
        var result = PreClosureSettlementCalculator.calculate(terms("FLOATING", "Y", "PERCENTAGE",
                new BigDecimal("2"), new BigDecimal("18"), today.minusDays(10)));
        assertEquals(new BigDecimal("25082.19"), result.total());
        assertEquals(new BigDecimal("0.00"), result.charges());
        assertEquals(new BigDecimal("0.00"), result.tax());
        assertEquals("NONE", result.appliedChargeType());
    }

    @Test void waivedChargesHaveNoTax() {
        var result = PreClosureSettlementCalculator.calculate(terms("FIXED", "N", "PERCENTAGE",
                new BigDecimal("2"), null, today.minusDays(10)));
        assertEquals(new BigDecimal("25082.19"), result.total());
        assertEquals(new BigDecimal("0.00"), result.tax());
    }

    @Test void noAdditionalInterestWhenAlreadyPaidThroughSettlementDate() {
        var result = PreClosureSettlementCalculator.calculate(terms("FIXED", "N", "NONE",
                BigDecimal.ZERO, null, today));
        assertEquals(new BigDecimal("0.00"), result.interest());
        assertEquals(new BigDecimal("25000.00"), result.total());
    }

    @Test void taxRateComesFromProductAndAppliesOnlyToTheFee() {
        var result = PreClosureSettlementCalculator.calculate(terms("FIXED", "Y", "FIXED",
                new BigDecimal("100"), new BigDecimal("5"), today));
        assertEquals(new BigDecimal("5.00"), result.tax());
        assertEquals(new BigDecimal("25105.00"), result.total());
    }

    @Test void missingPaidThroughDateIsNotGuessedFromLoanAge() {
        assertThrows(IllegalArgumentException.class, () -> PreClosureSettlementCalculator.calculate(
                terms("FIXED", "N", "NONE", BigDecimal.ZERO, null, null)));
    }

    @Test void invalidDatesDoNotCreateNegativeInterest() {
        assertThrows(IllegalArgumentException.class, () -> PreClosureSettlementCalculator.calculate(
                terms("FIXED", "N", "NONE", BigDecimal.ZERO, null, today.plusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> PreClosureSettlementCalculator.calculate(
                terms("FIXED", "N", "NONE", BigDecimal.ZERO, null, today.minusMonths(16))));
    }

    @Test void feePermissionAndTaxCannotBeAssumed() {
        assertThrows(IllegalArgumentException.class, () -> PreClosureSettlementCalculator.calculate(
                terms("FIXED", null, "PERCENTAGE", new BigDecimal("2"), new BigDecimal("18"), today)));
        assertThrows(IllegalArgumentException.class, () -> PreClosureSettlementCalculator.calculate(
                terms("FIXED", "Y", "PERCENTAGE", new BigDecimal("2"), null, today)));
        assertThrows(IllegalArgumentException.class, () -> PreClosureSettlementCalculator.calculate(
                terms(null, "Y", "PERCENTAGE", new BigDecimal("2"), new BigDecimal("18"), today)));
    }

    @Test void actual365UsesCalendarDaysAcrossLeapDay() {
        var terms = new PreClosureSettlementCalculator.Terms(new BigDecimal("25000"), new BigDecimal("12"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1),
                "FIXED", "NON_BUSINESS", "N", "NONE", BigDecimal.ZERO, null);
        var result = PreClosureSettlementCalculator.calculate(terms);
        assertEquals(2, result.interestDays());
        assertEquals(new BigDecimal("16.44"), result.interest());
    }
}
