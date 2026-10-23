package com.clearclose.exit.completion;

import com.clearclose.exit.preclosure.PreClosureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;

/** Orchestration deliberately has no enclosing database transaction. */
@Service
public class FinalExitCompletionService {
    private static final Logger log = LoggerFactory.getLogger(FinalExitCompletionService.class);
    private final JdbcTemplate jdbc;
    private final PreClosureService workflow;
    private long cursor;
    private final FinalExitStateService state;
    private final LoanAccountClosureClient loans;
    public FinalExitCompletionService(FinalExitStateService state, LoanAccountClosureClient loans, JdbcTemplate jdbc, PreClosureService workflow) {
        this.jdbc = jdbc;
        this.workflow = workflow;
        this.state = state;
        this.loans = loans;
    }

    public boolean complete(long paymentId) {
        FinalExitStateService.Work work = state.load(paymentId);
        if ("COMPLETED".equals(work.requestStatus())) return state.finish(paymentId, null);
        // Even after a lost HTTP response this is safe: LoanAccount confirms the same
        // workflow's existing closure, and never treats another closure as our success.
        var closure = loans.close(work);
        return state.finish(paymentId, closure);
    }

    // Foreclosure completion and durable retries.
    private record PreClosureWork(long borrowerId, long loanId, long paymentId, long quoteId) {}

    // Deliberately no @Transactional: LoanAccount must acquire its own database locks.
    public void tryCompletePreClosure(long requestId) {
        try {
            var rows = jdbc.query("""
                SELECT l.BORROWER_ID, q.LOAN_ID, p.PAYMENT_ID, q.QUOTE_ID FROM LOAN_CLOSURE_REQUEST c
                JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
                JOIN SETTLEMENT_QUOTE q ON q.CLOSURE_REQUEST_ID = c.CLOSURE_REQUEST_ID AND q.LOAN_ID = c.LOAN_ID
                JOIN PAYMENT p ON p.QUOTE_ID = q.QUOTE_ID
                WHERE c.CLOSURE_REQUEST_ID = ? AND c.CLOSURE_TYPE = 'FORECLOSURE'
                  AND c.STATUS = 'PAYMENT_PENDING' AND q.QUOTE_TYPE = 'CLOSURE'
                  AND q.QUOTE_STATUS = 'PAID' AND p.PAYMENT_STATUS = 'SUCCESS'
                  AND EXISTS (SELECT 1 FROM AUDIT_LOG a WHERE a.PAYMENT_ID = p.PAYMENT_ID
                      AND a.QUOTE_ID = q.QUOTE_ID AND a.CLOSURE_REQUEST_ID = c.CLOSURE_REQUEST_ID
                      AND a.ACTION = 'PRE_CLOSURE_PAYMENT_VERIFIED')
                """, (rs, n) -> new PreClosureWork(rs.getLong("BORROWER_ID"), rs.getLong("LOAN_ID"), rs.getLong("PAYMENT_ID"), rs.getLong("QUOTE_ID")), requestId);
            if (rows.isEmpty()) return;
            if (rows.size() != 1) throw new IllegalStateException("Ambiguous pre-closure payment");
            var work = rows.getFirst();
            loans.closePreClosure(work.loanId(), work.paymentId(), work.quoteId(), requestId);
            workflow.complete(work.borrowerId(), requestId, work.loanId(), work.quoteId(), work.paymentId());
        } catch (RuntimeException ex) {
            log.warn("Pre-closure completion pending requestId={} errorType={}", requestId, ex.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${clearclose.preclosure.retry-delay-ms:30000}",
        initialDelayString = "${clearclose.preclosure.retry-delay-ms:30000}")
    public void recoverPreClosures() {
        try {
            var pending = jdbc.queryForList("""
                SELECT DISTINCT c.CLOSURE_REQUEST_ID FROM LOAN_CLOSURE_REQUEST c
                JOIN SETTLEMENT_QUOTE q ON q.CLOSURE_REQUEST_ID = c.CLOSURE_REQUEST_ID
                JOIN PAYMENT p ON p.QUOTE_ID = q.QUOTE_ID
                WHERE c.CLOSURE_TYPE = 'FORECLOSURE' AND c.STATUS = 'PAYMENT_PENDING'
                  AND q.QUOTE_STATUS = 'PAID' AND p.PAYMENT_STATUS = 'SUCCESS' AND c.CLOSURE_REQUEST_ID > ?
                ORDER BY c.CLOSURE_REQUEST_ID FETCH FIRST 20 ROWS ONLY
                """, Long.class, cursor);
            if (pending.isEmpty()) cursor = 0;
            for (long id : pending) { cursor = id; tryCompletePreClosure(id); }
        } catch (RuntimeException ex) {
            log.warn("Pre-closure recovery scan failed errorType={}", ex.getClass().getSimpleName());
        }
    }
}
