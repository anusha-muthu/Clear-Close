package com.clearclose.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CoolingOffLoanClosureService {
    private final JdbcTemplate jdbc;
    public CoolingOffLoanClosureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record ClosureRequest(Long paymentId, Long quoteId, Long exitRequestId) {}
    public record ClosureResult(long loanId, long paymentId, long quoteId, long exitRequestId,
            String loanStatus, BigDecimal outstandingAmount, LocalDateTime closureDate) {}
    private record Workflow(long borrowerId, String loanStatus, BigDecimal outstanding, LocalDateTime closureDate,
            String requestStatus, boolean approved, boolean verified, String quoteStatus,
            BigDecimal amount, BigDecimal total, boolean accepted) {}

    private boolean positive(Long id) { return id != null && id > 0; }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    @Transactional
    public ClosureResult close(Long loanId, ClosureRequest request) {
        if (!positive(loanId) || request == null || !positive(request.paymentId())
                || !positive(request.quoteId()) || !positive(request.exitRequestId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid workflow IDs are required.");
        }
        // Same request-first lock order as Exit Service; no remote calls inside this transaction.
        var locked = jdbc.queryForList("""
            SELECT EXIT_REQUEST_ID FROM COOLING_OFF_REQUEST
            WHERE EXIT_REQUEST_ID = ? AND LOAN_ID = ? FOR UPDATE
            """, Long.class, request.exitRequestId(), loanId);
        if (locked.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan exit workflow not found.");
        var rows = jdbc.query("""
            SELECT l.BORROWER_ID, l.LOAN_STATUS, l.OUTSTANDING_AMOUNT, l.CLOSURE_DATE,
                   c.STATUS AS REQUEST_STATUS, q.QUOTE_STATUS, p.AMOUNT, q.TOTAL_AMOUNT,
                   CASE WHEN c.OFFICER_ID IS NOT NULL AND c.DECIDED_AT IS NOT NULL THEN 1 ELSE 0 END AS APPROVED,
                   CASE WHEN p.PAYMENT_STATUS = 'SUCCESS' AND p.RAZORPAY_PAYMENT_ID IS NOT NULL
                             AND p.CONFIRMED_AT IS NOT NULL THEN 1 ELSE 0 END AS VERIFIED,
                   CASE WHEN q.ACCEPTED_AT IS NOT NULL THEN 1 ELSE 0 END AS ACCEPTED
            FROM LOAN_ACCOUNT l
            JOIN COOLING_OFF_REQUEST c ON c.LOAN_ID = l.LOAN_ID
            JOIN SETTLEMENT_QUOTE q ON q.EXIT_REQUEST_ID = c.EXIT_REQUEST_ID AND q.LOAN_ID = l.LOAN_ID
            JOIN PAYMENT p ON p.QUOTE_ID = q.QUOTE_ID
            WHERE l.LOAN_ID = ? AND c.EXIT_REQUEST_ID = ? AND q.QUOTE_ID = ?
              AND p.PAYMENT_ID = ? AND q.QUOTE_TYPE = 'COOLING_OFF'
            FOR UPDATE OF l.LOAN_STATUS, q.QUOTE_STATUS, p.PAYMENT_STATUS
            """, (rs, row) -> {
                Timestamp date = rs.getTimestamp("CLOSURE_DATE");
                return new Workflow(rs.getLong("BORROWER_ID"), rs.getString("LOAN_STATUS"),
                    rs.getBigDecimal("OUTSTANDING_AMOUNT"), date == null ? null : date.toLocalDateTime(),
                    rs.getString("REQUEST_STATUS"), rs.getInt("APPROVED") == 1, rs.getInt("VERIFIED") == 1,
                    rs.getString("QUOTE_STATUS"), rs.getBigDecimal("AMOUNT"), rs.getBigDecimal("TOTAL_AMOUNT"),
                    rs.getInt("ACCEPTED") == 1);
            }, loanId, request.exitRequestId(), request.quoteId(), request.paymentId());
        if (rows.size() != 1) throw conflict("Payment does not belong to this loan exit workflow.");
        Workflow workflow = rows.getFirst();
        if (!workflow.verified() || !workflow.approved() || !workflow.accepted()
                || !"PAID".equals(workflow.quoteStatus()) || workflow.amount().signum() <= 0
                || workflow.amount().compareTo(workflow.total()) != 0) {
            throw conflict("Verified full settlement and recorded officer approval are required.");
        }
        Integer confirmed = jdbc.queryForObject("""
            SELECT COUNT(*) FROM AUDIT_LOG WHERE PAYMENT_ID = ? AND QUOTE_ID = ?
              AND EXIT_REQUEST_ID = ? AND ACTION = 'PAYMENT_EVENT_CONFIRMED'
            """, Integer.class, request.paymentId(), request.quoteId(), request.exitRequestId());
        if (confirmed == null || confirmed != 1) throw conflict("Exit Service has not confirmed this payment.");
        Integer closedByThisWorkflow = jdbc.queryForObject("""
            SELECT COUNT(*) FROM AUDIT_LOG WHERE PAYMENT_ID = ? AND QUOTE_ID = ? AND EXIT_REQUEST_ID = ?
              AND ACTION = 'COOLING_OFF_LOAN_CLOSED'
            """, Integer.class, request.paymentId(), request.quoteId(), request.exitRequestId());
        if ("CLOSED".equals(workflow.loanStatus())) {
            if (closedByThisWorkflow == null || closedByThisWorkflow != 1 || workflow.closureDate() == null
                    || workflow.outstanding().signum() != 0
                    || !("APPROVED".equals(workflow.requestStatus()) || "COMPLETED".equals(workflow.requestStatus()))) {
                throw conflict("Loan was closed by a different workflow or closure data is inconsistent.");
            }
            return new ClosureResult(loanId, request.paymentId(), request.quoteId(), request.exitRequestId(),
                "CLOSED", workflow.outstanding(), workflow.closureDate());
        }
        if (!"ACTIVE".equals(workflow.loanStatus()) || !"APPROVED".equals(workflow.requestStatus())
                || (closedByThisWorkflow != null && closedByThisWorkflow > 0)) {
            throw conflict("Only an active loan with an approved cooling-off request can be closed.");
        }
        int changed = jdbc.update("""
            UPDATE LOAN_ACCOUNT SET LOAN_STATUS = 'CLOSED', OUTSTANDING_AMOUNT = 0,
                CLOSURE_DATE = SYSDATE, UPDATED_AT = SYSTIMESTAMP
            WHERE LOAN_ID = ? AND LOAN_STATUS = 'ACTIVE'
            """, loanId);
        if (changed != 1) throw conflict("Loan state changed; retry with the same workflow.");
        jdbc.update("""
            INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS,
                                   PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID)
            VALUES (?, ?, 'PAYMENT', 'COOLING_OFF_LOAN_CLOSED', ?, ?, ?, ?)
            """, workflow.borrowerId(), request.paymentId(), "loanId=" + loanId,
                request.paymentId(), request.quoteId(), request.exitRequestId());
        LocalDateTime closedAt = jdbc.queryForObject("SELECT CLOSURE_DATE FROM LOAN_ACCOUNT WHERE LOAN_ID = ?",
            Timestamp.class, loanId).toLocalDateTime();
        return new ClosureResult(loanId, request.paymentId(), request.quoteId(), request.exitRequestId(),
            "CLOSED", BigDecimal.ZERO, closedAt);
    }
}
