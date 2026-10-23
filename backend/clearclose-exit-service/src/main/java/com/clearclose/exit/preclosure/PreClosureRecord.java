package com.clearclose.exit.preclosure;

import java.time.LocalDateTime;

public record PreClosureRecord(
    Long closureRequestId,
    Long loanId,
    String closureType,
    String status,
    String reason,
    Long officerId,
    LocalDateTime createdAt,
    PreClosureQuote quote
) {}