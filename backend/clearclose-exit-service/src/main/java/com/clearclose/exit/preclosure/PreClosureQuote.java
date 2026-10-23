package com.clearclose.exit.preclosure;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PreClosureQuote(
    Long quoteId,
    Long closureRequestId,
    Long loanId,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal chargesAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    String quoteStatus,
    LocalDateTime validUntil,
    boolean expired
) {}