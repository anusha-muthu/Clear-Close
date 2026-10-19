
package com.clearclose.exit.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OfficerDashboardDto {

    // Details of a cooling-off request
    public record RequestDetails(
        Long exitRequestId,
        Long loanId,
        String loanReference,
        Long borrowerId,
        String borrowerName,
        String status,
        String reason,
        Long officerId,
        LocalDateTime requestDate,
        LocalDateTime expiryDate,
        BigDecimal principalAmount,
        BigDecimal outstandingAmount,
        BigDecimal interestRate,
        Long quoteId,
        BigDecimal settlementAmount,
        String quoteStatus,
        LocalDateTime quoteValidUntil
    ) {}

    // Dashboard summary
    public record DashboardSummary(
        long totalRequests,
        long pending,
        long underReview,
        long approved,
        long completed,
        long unassigned,
        long assignedToMe
    ) {}
}
