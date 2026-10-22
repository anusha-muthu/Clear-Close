package com.clearclose.exit.completion;

import com.clearclose.exit.events.LoanExitEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class FinalExitRecovery {
    private static final Logger log = LoggerFactory.getLogger(FinalExitRecovery.class);
    private final JdbcTemplate jdbc;
    private final FinalExitCompletionService completion;
    private final LoanExitEventProducer events;
    private final int batchSize;
    private long lastPaymentId;
    public FinalExitRecovery(JdbcTemplate jdbc, FinalExitCompletionService completion, LoanExitEventProducer events,
            @Value("${clearclose.completion.retry-batch-size}") int batchSize) {
        this.jdbc = jdbc;
        this.completion = completion;
        this.events = events;
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("Completion batch size must be 1..1000.");
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${clearclose.completion.retry-delay-ms}",
        initialDelayString = "${clearclose.completion.retry-delay-ms}")
    public void recover() {
        try {
            // A confirmed audit + APPROVED request is a durable pending work item,
            // even if the Kafka record has already moved to the existing DLT.
            var pending = jdbc.queryForList("""
                SELECT DISTINCT a.PAYMENT_ID FROM AUDIT_LOG a
                JOIN PAYMENT p ON p.PAYMENT_ID = a.PAYMENT_ID
                JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID
                JOIN COOLING_OFF_REQUEST c ON c.EXIT_REQUEST_ID = q.EXIT_REQUEST_ID
                WHERE a.ACTION = 'PAYMENT_EVENT_CONFIRMED' AND p.PAYMENT_STATUS = 'SUCCESS'
                  AND q.QUOTE_STATUS = 'PAID' AND c.STATUS = 'APPROVED' AND a.PAYMENT_ID > ?
                ORDER BY a.PAYMENT_ID FETCH FIRST ? ROWS ONLY
                """, Long.class, lastPaymentId, batchSize);
            if (pending.isEmpty()) lastPaymentId = 0;
            for (long paymentId : pending) {
                // Cursor only provides fair scanning; pending work itself lives in Oracle.
                lastPaymentId = paymentId;
                try {
                    if (completion.complete(paymentId)) log.info("Recovered final cooling-off exit paymentId={}", paymentId);
                } catch (RuntimeException failure) {
                    log.warn("Final completion remains pending paymentId={} reason={}", paymentId,
                        failure instanceof IllegalStateException ? failure.getMessage() : failure.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Final completion recovery scan failed errorType={}", failure.getClass().getSimpleName());
        }
        try {
            events.retryPending(batchSize);
        } catch (RuntimeException failure) {
            log.warn("Completion event recovery scan failed errorType={}", failure.getClass().getSimpleName());
        }
    }
}
