
package com.clearclose.exit.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementQuoteDto(
    Long quoteId,
    Long loanId,
    Long exitRequestId,
    String quoteType,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal chargesAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    String quoteStatus,
    LocalDateTime validUntil,
    LocalDateTime acceptedAt,
    LocalDateTime createdAt,
    boolean expired
) {}
