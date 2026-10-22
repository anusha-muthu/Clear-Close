
package com.clearclose.noc.dto;

public record NocEligibilityResponse(
    Long loanId,
    boolean eligible,
    String reason
) {}
