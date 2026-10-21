package com.clearclose.exit.completion;

import java.math.BigDecimal;
import com.clearclose.exit.events.LoanExitEventProducer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short local transactions only. LOAN_ACCOUNT is read here, never updated. */
@Service
public class FinalExitStateService {
    private final JdbcTemplate jdbc;
    private final LoanExitEventProducer events;
    public FinalExitStateService(JdbcTemplate jdbc, LoanExitEventProducer events) { this.jdbc = jdbc; this.events = events; }

    public record Work(long paymentId, long quoteId, long loanId, long exitRequestId, long borrowerId,
            String requestStatus, String loanStatus, BigDecimal outstanding, boolean closureRecorded) {}

    private Work read(long paymentId) {
        var rows = jdbc.query("""
            SELECT p.PAYMENT_ID, q.QUOTE_ID, q.LOAN_ID, q.EXIT_REQUEST_ID, l.BORROWER_ID,
                   c.STATUS AS REQUEST_STATUS, l.LOAN_STATUS, l.OUTSTANDING_AMOUNT,
                   (SELECT COUNT(*) FROM AUDIT_LOG a WHERE a.PAYMENT_ID = p.PAYMENT_ID
                    AND a.QUOTE_ID = q.QUOTE_ID AND a.EXIT_REQUEST_ID = c.EXIT_REQUEST_ID
                    AND a.ACTION = 'COOLING_OFF_LOAN_CLOSED') AS CLOSED_BY_WORKFLOW
            FROM PAYMENT p
            JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID
            JOIN COOLING_OFF_REQUEST c ON c.EXIT_REQUEST_ID = q.EXIT_REQUEST_ID AND c.LOAN_ID = q.LOAN_ID
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = q.LOAN_ID
            WHERE p.PAYMENT_ID = ? AND p.PAYMENT_STATUS = 'SUCCESS'
              AND p.RAZORPAY_PAYMENT_ID IS NOT NULL AND p.CONFIRMED_AT IS NOT NULL
              AND p.AMOUNT > 0 AND p.AMOUNT = q.TOTAL_AMOUNT
              AND q.QUOTE_TYPE = 'COOLING_OFF' AND q.QUOTE_STATUS = 'PAID' AND q.ACCEPTED_AT IS NOT NULL
              AND c.OFFICER_ID IS NOT NULL AND c.DECIDED_AT IS NOT NULL
              AND (l.LOAN_STATUS <> 'CLOSED' OR l.CLOSURE_DATE IS NOT NULL)
              AND EXISTS (SELECT 1 FROM AUDIT_LOG a WHERE a.PAYMENT_ID = p.PAYMENT_ID
                  AND a.QUOTE_ID = q.QUOTE_ID AND a.EXIT_REQUEST_ID = c.EXIT_REQUEST_ID
                  AND a.ACTION = 'PAYMENT_EVENT_CONFIRMED')
            """, (rs, row) -> new Work(rs.getLong("PAYMENT_ID"), rs.getLong("QUOTE_ID"), rs.getLong("LOAN_ID"),
                rs.getLong("EXIT_REQUEST_ID"), rs.getLong("BORROWER_ID"), rs.getString("REQUEST_STATUS"),
                rs.getString("LOAN_STATUS"), rs.getBigDecimal("OUTSTANDING_AMOUNT"), rs.getInt("CLOSED_BY_WORKFLOW") == 1), paymentId);
        if (rows.size() != 1) throw new IllegalStateException("Verified paid quote and recorded officer approval are required for final completion.");
        Work work = rows.getFirst();
        if (!("APPROVED".equals(work.requestStatus()) || "COMPLETED".equals(work.requestStatus()))
                || !("ACTIVE".equals(work.loanStatus()) || "CLOSED".equals(work.loanStatus()))
                || ("COMPLETED".equals(work.requestStatus()) && (!"CLOSED".equals(work.loanStatus())
                    || !work.closureRecorded() || work.outstanding().signum() != 0))) {
            throw new IllegalStateException("Workflow is not eligible for final cooling-off completion.");
        }
        return work;
    }

    @Transactional(readOnly = true)
    public Work load(long paymentId) { return read(paymentId); }

    @Transactional
    public boolean finish(long paymentId, LoanAccountClosureClient.ClosureResult result) {
        // Lock request first. LoanAccount's transaction is already finished at this point.
        var locked = jdbc.queryForList("""
            SELECT c.EXIT_REQUEST_ID FROM COOLING_OFF_REQUEST c WHERE c.EXIT_REQUEST_ID = (
                SELECT q.EXIT_REQUEST_ID FROM SETTLEMENT_QUOTE q JOIN PAYMENT p ON p.QUOTE_ID = q.QUOTE_ID
                WHERE p.PAYMENT_ID = ?
            ) FOR UPDATE
            """, Long.class, paymentId);
        if (locked.isEmpty()) throw new IllegalStateException("Exit request disappeared before completion.");
        Work work = read(paymentId);
        if ("COMPLETED".equals(work.requestStatus()) && result == null) {
            // Durable closure evidence was already checked by read(); no remote dependency on a replay.
            events.prepareAfterFinalCompletion(paymentId);
            return false;
        }
        if (result == null || !Long.valueOf(work.loanId()).equals(result.loanId())
                || !Long.valueOf(work.paymentId()).equals(result.paymentId())
                || !Long.valueOf(work.quoteId()).equals(result.quoteId())
                || !Long.valueOf(work.exitRequestId()).equals(result.exitRequestId())
                || !"CLOSED".equals(result.loanStatus()) || result.outstandingAmount() == null
                || result.outstandingAmount().signum() != 0 || result.closureDate() == null
                || !"CLOSED".equals(work.loanStatus()) || work.outstanding().signum() != 0 || !work.closureRecorded()) {
            throw new IllegalStateException("Authoritative loan closure has not been confirmed for this workflow.");
        }
        boolean changed = "APPROVED".equals(work.requestStatus());
        if (changed) {
            int updated = jdbc.update("""
                UPDATE COOLING_OFF_REQUEST SET STATUS = 'COMPLETED', UPDATED_AT = SYSTIMESTAMP
                WHERE EXIT_REQUEST_ID = ? AND STATUS = 'APPROVED'
                """, work.exitRequestId());
            if (updated != 1) throw new IllegalStateException("Exit request changed before completion.");
            jdbc.update("""
                INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS,
                                       PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID)
                VALUES (?, ?, 'COOLING_OFF_REQUEST', 'COOLING_OFF_EXIT_COMPLETED', ?, ?, ?, ?)
                """, work.borrowerId(), work.exitRequestId(), "loanId=" + work.loanId(),
                    work.paymentId(), work.quoteId(), work.exitRequestId());
        }
        events.prepareAfterFinalCompletion(paymentId);
        return changed;
    }
}
