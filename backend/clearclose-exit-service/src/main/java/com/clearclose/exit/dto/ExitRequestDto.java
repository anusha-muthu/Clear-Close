
package com.clearclose.exit.dto;

import java.time.LocalDateTime;

public class ExitRequestDto {

    // Data received from borrower
    public record CreateRequest(
        Long loanId,
        String reason
    ) {}

    // Data returned to frontend
    public record RequestResponse(
        Long exitRequestId,
        Long loanId,
        String loanReference,
        Long officerId,
        String status,
        String reason,
        LocalDateTime requestDate,
        LocalDateTime expiryDate,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
