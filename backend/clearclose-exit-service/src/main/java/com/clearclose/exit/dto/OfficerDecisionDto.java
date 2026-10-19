
package com.clearclose.exit.dto;

import java.time.LocalDateTime;

public class OfficerDecisionDto {

    // Data sent by the officer
    public record DecisionRequest(
        String decision,
        String remarks
    ) {}

    // Data returned after the decision
    public record DecisionResponse(
        Long exitRequestId,
        Long loanId,
        Long officerId,
        String status,
        String remarks,
        LocalDateTime decidedAt
    ) {}
}
