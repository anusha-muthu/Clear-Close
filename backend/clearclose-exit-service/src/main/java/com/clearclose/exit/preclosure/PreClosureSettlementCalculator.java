package com.clearclose.exit.preclosure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Actual/365 on outstanding principal unchanged since interestPaidThrough. */
public final class PreClosureSettlementCalculator {
    private PreClosureSettlementCalculator() {}

    public record Terms(BigDecimal outstandingPrincipal, BigDecimal annualInterestRate,
            LocalDate disbursementDate, LocalDate interestPaidThrough, LocalDate settlementDate,
            String interestRateType, String loanPurpose, String prepaymentChargeAllowed,
            String chargeType, BigDecimal chargeValue, BigDecimal taxRate) {}

    public record Calculation(BigDecimal principal, BigDecimal interest, BigDecimal charges,
            BigDecimal tax, BigDecimal total, long interestDays, String appliedChargeType,
            BigDecimal appliedChargeValue, BigDecimal appliedTaxRate, String chargePolicyReason) {}

    public static Calculation calculate(Terms terms) {
        require(terms != null, "Settlement terms are required");
        require(terms.outstandingPrincipal() != null && terms.outstandingPrincipal().signum() > 0,
                "Outstanding principal must be positive");
        require(terms.annualInterestRate() != null && terms.annualInterestRate().signum() >= 0,
                "A non-negative annual interest rate is required");
        require(terms.disbursementDate() != null && terms.settlementDate() != null,
                "Disbursement and settlement dates are required");
        require(terms.interestPaidThrough() != null,
                "Interest paid-through date is missing. Record the verified paid-through date before generating a quotation");
        require(!terms.interestPaidThrough().isBefore(terms.disbursementDate()),
                "Interest paid-through date cannot precede disbursement");
        require(!terms.interestPaidThrough().isAfter(terms.settlementDate()),
                "Interest paid-through date cannot be after the settlement date");

        BigDecimal principal = money(terms.outstandingPrincipal());
        long days = ChronoUnit.DAYS.between(terms.interestPaidThrough(), terms.settlementDate());
        BigDecimal interest = principal.multiply(terms.annualInterestRate()).multiply(BigDecimal.valueOf(days))
                .divide(new BigDecimal("36500"), 2, RoundingMode.HALF_UP);

        String appliedType = "NONE";
        BigDecimal appliedValue = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        String reason;
        // A product fee must never override a known protected loan category.
        if ("FLOATING".equals(terms.interestRateType()) && "NON_BUSINESS".equals(terms.loanPurpose())) {
            reason = "No prepayment charge: floating-rate loan to an individual for non-business use";
        } else if ("N".equals(terms.prepaymentChargeAllowed())) {
            reason = "Prepayment charge prohibited or waived for this loan";
        } else if ("NONE".equals(terms.chargeType())) {
            reason = "No foreclosure charge configured in the product terms";
        } else {
            require("Y".equals(terms.prepaymentChargeAllowed()),
                    "Foreclosure fee permission is unverified. Record whether charges are permitted for this loan");
            require("FIXED".equals(terms.interestRateType()) || "FLOATING".equals(terms.interestRateType()),
                    "Fixed/floating interest rate type is required before applying a foreclosure fee");
            require("BUSINESS".equals(terms.loanPurpose()) || "NON_BUSINESS".equals(terms.loanPurpose()),
                    "Loan purpose is required before applying a foreclosure fee");
            require(terms.chargeValue() != null && terms.chargeValue().signum() >= 0,
                    "A non-negative foreclosure charge value is required");
            appliedType = terms.chargeType();
            appliedValue = terms.chargeValue();
            charges = switch (appliedType == null ? "" : appliedType) {
                case "FIXED" -> money(appliedValue);
                case "PERCENTAGE" -> {
                    require(appliedValue.compareTo(new BigDecimal("100")) <= 0,
                            "Foreclosure percentage cannot exceed 100");
                    yield principal.multiply(appliedValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                }
                default -> throw new IllegalArgumentException("Unsupported foreclosure charge rule");
            };
            reason = "Foreclosure charge permitted under the recorded loan terms and eligibility review";
        }
        BigDecimal taxRate = BigDecimal.ZERO;
        if (charges.signum() > 0) {
            require(terms.taxRate() != null && terms.taxRate().signum() >= 0
                            && terms.taxRate().compareTo(new BigDecimal("100")) <= 0,
                    "Configure the applicable tax rate on foreclosure charges (0 to 100)");
            taxRate = terms.taxRate();
        }
        BigDecimal tax = charges.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new Calculation(principal, interest, money(charges), tax,
                principal.add(interest).add(charges).add(tax), days, appliedType, appliedValue, taxRate, reason);
    }

    private static BigDecimal money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP); }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
