package com.clearclose.exit.events;

import java.time.Instant;

/** Contract only: no current payment or approval workflow publishes this event. */
public record LoanExitCompletedEvent(
    String eventId,
    String eventType,
    Integer eventVersion,
    Long loanId,
    Long exitRequestId,
    Long quoteId,
    Long paymentId,
    String completionType,
    Instant occurredAt
) {
    public static final String TYPE = "LOAN_EXIT_COMPLETED";
    public static final int VERSION = 1;
}
