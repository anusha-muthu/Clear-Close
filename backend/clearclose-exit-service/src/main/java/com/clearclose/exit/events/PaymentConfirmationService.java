package com.clearclose.exit.events;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentConfirmationService {
    private final JdbcTemplate jdbc;

    public PaymentConfirmationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private record VerifiedPayment(long borrowerId, String paymentStatus, BigDecimal amount,
            BigDecimal total, String quoteStatus, String requestStatus, String loanStatus,
            boolean providerConfirmed, boolean approvalRecorded) {}

    private boolean positive(Long value) { return value != null && value > 0; }

    private void validate(String key, PaymentEvent event) {
        if (event == null || !PaymentEvent.TYPE.equals(event.eventType())
                || !Integer.valueOf(PaymentEvent.VERSION).equals(event.eventVersion())
                || !positive(event.paymentId()) || !positive(event.quoteId())
                || !positive(event.loanId()) || !positive(event.exitRequestId())
                || event.amount() == null || event.amount().signum() <= 0
                || !"INR".equals(event.currency()) || event.occurredAt() == null
                || !event.quoteId().toString().equals(key)) {
            throw new IllegalArgumentException("Invalid PaymentSucceeded contract or message key.");
        }
        try {
            if (!UUID.fromString(event.eventId()).toString().equals(event.eventId())) {
                throw new IllegalArgumentException("Non-canonical event ID.");
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid PaymentSucceeded event ID.");
        }
    }

    @Transactional
    public boolean confirm(String key, PaymentEvent event) {
        validate(key, event);
        // Match the existing acceptance/payment lock order; duplicate deliveries serialize here.
        var requests = jdbc.queryForList("""
            SELECT EXIT_REQUEST_ID FROM COOLING_OFF_REQUEST WHERE EXIT_REQUEST_ID = ? FOR UPDATE
            """, Long.class, event.exitRequestId());
        if (requests.isEmpty()) throw new IllegalArgumentException("Event exit request does not exist.");

        var records = jdbc.query("""
            SELECT l.BORROWER_ID, p.PAYMENT_STATUS, p.AMOUNT, q.TOTAL_AMOUNT, q.QUOTE_STATUS,
                   c.STATUS AS REQUEST_STATUS, l.LOAN_STATUS,
                   CASE WHEN p.RAZORPAY_PAYMENT_ID IS NOT NULL AND p.CONFIRMED_AT IS NOT NULL
                        THEN 1 ELSE 0 END AS PROVIDER_CONFIRMED,
                   CASE WHEN c.OFFICER_ID IS NOT NULL AND c.DECIDED_AT IS NOT NULL
                             AND q.ACCEPTED_AT IS NOT NULL THEN 1 ELSE 0 END AS APPROVAL_RECORDED
            FROM PAYMENT p
            JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID
            JOIN COOLING_OFF_REQUEST c ON c.EXIT_REQUEST_ID = q.EXIT_REQUEST_ID AND c.LOAN_ID = q.LOAN_ID
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = q.LOAN_ID
            WHERE p.PAYMENT_ID = ? AND q.QUOTE_ID = ? AND q.LOAN_ID = ?
              AND q.EXIT_REQUEST_ID = ? AND q.QUOTE_TYPE = 'COOLING_OFF'
            FOR UPDATE OF q.QUOTE_STATUS, p.PAYMENT_STATUS
            """, (rs, row) -> new VerifiedPayment(rs.getLong("BORROWER_ID"), rs.getString("PAYMENT_STATUS"),
                rs.getBigDecimal("AMOUNT"), rs.getBigDecimal("TOTAL_AMOUNT"), rs.getString("QUOTE_STATUS"),
                rs.getString("REQUEST_STATUS"), rs.getString("LOAN_STATUS"), rs.getInt("PROVIDER_CONFIRMED") == 1,
                rs.getInt("APPROVAL_RECORDED") == 1),
                event.paymentId(), event.quoteId(), event.loanId(), event.exitRequestId());
        if (records.size() != 1) throw new IllegalArgumentException("Payment/quote/loan/request relationships do not match.");
        VerifiedPayment payment = records.getFirst();
        if (!"SUCCESS".equals(payment.paymentStatus()) || !payment.providerConfirmed() || !payment.approvalRecorded()
                || event.amount().compareTo(payment.amount()) != 0
                || event.amount().compareTo(payment.total()) != 0) {
            throw new IllegalArgumentException("Event is not backed by a matching verified INR payment.");
        }
        // INR is the only currency the verified-payment workflow accepts and stores.
        // Event identity/time must match the record made in that same successful transaction.
        Integer ready = jdbc.queryForObject("""
            SELECT COUNT(*) FROM AUDIT_LOG
            WHERE PAYMENT_ID = ? AND QUOTE_ID = ? AND ACTION = 'PAYMENT_EVENT_READY' AND DETAILS = ?
            """, Integer.class, event.paymentId(), event.quoteId(), event.auditIdentity());
        if (ready == null || ready != 1) throw new IllegalArgumentException("Payment event identity is not recorded by the producer.");

        Integer processed = jdbc.queryForObject("""
            SELECT COUNT(*) FROM AUDIT_LOG WHERE PAYMENT_ID = ? AND ACTION = 'PAYMENT_EVENT_CONFIRMED'
            """, Integer.class, event.paymentId());
        // Final completion is coordinated separately after this transaction commits.
        if (processed != null && processed > 0) return false;
        if (!"APPROVED".equals(payment.requestStatus()) || !"ACTIVE".equals(payment.loanStatus())
                || !("ACCEPTED".equals(payment.quoteStatus()) || "PAID".equals(payment.quoteStatus()))) {
            throw new IllegalArgumentException("Quote/request/loan state does not permit payment confirmation.");
        }

        if ("ACCEPTED".equals(payment.quoteStatus())) {
            int updated = jdbc.update("""
                UPDATE SETTLEMENT_QUOTE SET QUOTE_STATUS = 'PAID', UPDATED_AT = SYSTIMESTAMP
                WHERE QUOTE_ID = ? AND QUOTE_STATUS = 'ACCEPTED'
                """, event.quoteId());
            if (updated != 1) throw new IllegalStateException("Quote changed while confirming payment.");
        }
        jdbc.update("""
            INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS,
                                   PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID)
            VALUES (?, ?, 'PAYMENT', 'PAYMENT_EVENT_CONFIRMED', ?, ?, ?, ?)
            """, payment.borrowerId(), event.paymentId(), event.auditIdentity(), event.paymentId(),
                event.quoteId(), event.exitRequestId());
        // The consumer calls final completion after this local transaction commits.
        return true;
    }
}
