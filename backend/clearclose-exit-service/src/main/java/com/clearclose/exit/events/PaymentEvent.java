package com.clearclose.exit.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(
    String eventId,
    String eventType,
    Integer eventVersion,
    Long paymentId,
    Long quoteId,
    Long loanId,
    Long exitRequestId,
    BigDecimal amount,
    String currency,
    Instant occurredAt
) {
    public static final String TYPE = "PAYMENT_SUCCEEDED";
    public static final int VERSION = 1;

    // Stored in the existing audit log so manual replay preserves event identity.
    public String auditIdentity() {
        return "eventId=" + eventId + ";occurredAt=" + occurredAt;
    }
}
