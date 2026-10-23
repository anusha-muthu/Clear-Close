package com.clearclose.exit.preclosure;

public record PreClosureRequest(
    Long loanId,
    String reason
) {}