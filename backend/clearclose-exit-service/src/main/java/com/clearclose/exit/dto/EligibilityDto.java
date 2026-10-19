
package com.clearclose.exit.dto;

import java.time.LocalDateTime;

public record EligibilityDto(
    Long loanId,
    boolean eligible,
    String loanStatus,
    int coolingOffDays,
    LocalDateTime disbursementDate,
    LocalDateTime expiryDate,
    String reason
) {}
