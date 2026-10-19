
package com.clearclose.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanDto(

    Long loanId,

    String loanReference,

    String productType,

    BigDecimal principalAmount,

    BigDecimal outstandingAmount,

    BigDecimal interestRate,

    Integer tenureMonths,

    LocalDateTime disbursementDate,

    LocalDateTime maturityDate,

    Integer coolingOffDays,

    LocalDateTime coolingOffExpiry,

    String loanStatus,

    LocalDateTime closureDate

) {}
