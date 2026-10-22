package com.clearclose.exit.events;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Completion identity is durable in AUDIT_LOG; unpublished events are retried after restart. */
@Component
public class LoanExitEventProducer {
    private static final Logger log = LoggerFactory.getLogger(LoanExitEventProducer.class);
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;
    private final TransactionTemplate newTransaction;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public LoanExitEventProducer(JdbcTemplate jdbc, ApplicationEventPublisher events,
            KafkaTemplate<String, Object> kafka,
            @Value("${clearclose.events.completion-topic}") String topic,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.events = events;
        this.kafka = kafka;
        this.topic = topic;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void prepareAfterFinalCompletion(long paymentId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Completion publication requires the final-completion transaction.");
        }
        // Caller holds the request lock. One logical completion event per workflow.
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM AUDIT_LOG WHERE PAYMENT_ID = ? AND ACTION = 'LOAN_EXIT_EVENT_READY'",
            Integer.class, paymentId);
        if (existing != null && existing > 1) throw new IllegalStateException("Duplicate completion event identities require review.");
        if (existing != null && existing == 1) return;
        var rows = jdbc.query("""
            SELECT l.LOAN_ID, c.EXIT_REQUEST_ID, q.QUOTE_ID, p.PAYMENT_ID,
                   TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),
                       'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"') AS EVENT_TIME
            FROM PAYMENT p
            JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID
            JOIN COOLING_OFF_REQUEST c ON c.EXIT_REQUEST_ID = q.EXIT_REQUEST_ID AND c.LOAN_ID = q.LOAN_ID
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = q.LOAN_ID
            WHERE p.PAYMENT_ID = ? AND p.PAYMENT_STATUS = 'SUCCESS'
              AND p.RAZORPAY_PAYMENT_ID IS NOT NULL AND p.CONFIRMED_AT IS NOT NULL
              AND q.QUOTE_TYPE = 'COOLING_OFF' AND q.QUOTE_STATUS = 'PAID'
              AND c.STATUS = 'COMPLETED' AND c.OFFICER_ID IS NOT NULL AND c.DECIDED_AT IS NOT NULL
              AND l.LOAN_STATUS = 'CLOSED'
              AND l.OUTSTANDING_AMOUNT = 0 AND l.CLOSURE_DATE IS NOT NULL
              AND EXISTS (SELECT 1 FROM AUDIT_LOG a WHERE a.PAYMENT_ID = p.PAYMENT_ID
                  AND a.QUOTE_ID = q.QUOTE_ID AND a.EXIT_REQUEST_ID = c.EXIT_REQUEST_ID
                  AND a.ACTION = 'COOLING_OFF_LOAN_CLOSED')
            """, (rs, row) -> new LoanExitCompletedEvent(UUID.randomUUID().toString(),
                LoanExitCompletedEvent.TYPE, LoanExitCompletedEvent.VERSION, rs.getLong("LOAN_ID"),
                rs.getLong("EXIT_REQUEST_ID"), rs.getLong("QUOTE_ID"), rs.getLong("PAYMENT_ID"),
                "COOLING_OFF", Instant.parse(rs.getString("EVENT_TIME"))), paymentId);
        if (rows.size() != 1) throw new IllegalStateException("Final loan exit completion has not been confirmed.");
        var event = rows.getFirst();
        int prepared = jdbc.update("""
            INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS,
                                   PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID)
            SELECT l.BORROWER_ID, ?, 'COOLING_OFF_REQUEST', 'LOAN_EXIT_EVENT_READY', ?, ?, ?, ?
            FROM LOAN_ACCOUNT l WHERE l.LOAN_ID = ?
            """, event.exitRequestId(), identity(event), event.paymentId(), event.quoteId(), event.exitRequestId(), event.loanId());
        if (prepared != 1) throw new IllegalStateException("Completion event identity could not be persisted.");
        events.publishEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(LoanExitCompletedEvent event) {
        if (!inFlight.add(event.eventId())) return;
        try {
            kafka.send(topic, event.loanId().toString(), event).whenComplete((result, failure) -> {
                try {
                    if (failure == null) {
                        markPublished(event);
                        log.info("Published LoanExitCompleted eventId={} loanId={}", event.eventId(), event.loanId());
                    } else {
                        log.error("LoanExitCompleted send failed eventId={} loanId={}; durable retry remains pending.", event.eventId(), event.loanId());
                    }
                } catch (RuntimeException error) {
                    log.error("LoanExitCompleted acknowledgement could not be saved eventId={}; same event may be redelivered.", event.eventId());
                } finally {
                    inFlight.remove(event.eventId());
                }
            });
        } catch (RuntimeException failure) {
            inFlight.remove(event.eventId());
            log.error("LoanExitCompleted send failed eventId={} loanId={}; durable retry remains pending.", event.eventId(), event.loanId());
        }
    }

    private String identity(LoanExitCompletedEvent event) {
        return "eventId=" + event.eventId() + ";occurredAt=" + event.occurredAt();
    }

    private void markPublished(LoanExitCompletedEvent event) {
        // The Kafka acknowledgement can arrive on another thread or during AFTER_COMMIT.
        // A new transaction is essential; never write into already-committed resources.
        newTransaction.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT EXIT_REQUEST_ID FROM COOLING_OFF_REQUEST WHERE EXIT_REQUEST_ID = ? FOR UPDATE",
                Long.class, event.exitRequestId());
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM AUDIT_LOG WHERE PAYMENT_ID = ? AND ACTION = 'LOAN_EXIT_EVENT_PUBLISHED'",
                Integer.class, event.paymentId());
            if (count != null && count > 0) return;
            int marked = jdbc.update("""
                INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS,
                                       PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID)
                SELECT USER_ID, ENTITY_ID, ENTITY_TYPE, 'LOAN_EXIT_EVENT_PUBLISHED', DETAILS,
                       PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID FROM AUDIT_LOG
                WHERE PAYMENT_ID = ? AND ACTION = 'LOAN_EXIT_EVENT_READY' AND DETAILS = ?
                """, event.paymentId(), identity(event));
            if (marked != 1) throw new IllegalStateException("Completion event acknowledgement has no unique prepared event.");
        });
    }

    public void retryPending(int batchSize) {
        var pending = jdbc.query("""
            SELECT a.DETAILS, p.PAYMENT_ID, q.QUOTE_ID, q.LOAN_ID, c.EXIT_REQUEST_ID
            FROM AUDIT_LOG a
            JOIN PAYMENT p ON p.PAYMENT_ID = a.PAYMENT_ID
            JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID AND q.QUOTE_ID = a.QUOTE_ID
            JOIN COOLING_OFF_REQUEST c ON c.EXIT_REQUEST_ID = q.EXIT_REQUEST_ID AND c.EXIT_REQUEST_ID = a.EXIT_REQUEST_ID
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = q.LOAN_ID AND l.LOAN_ID = c.LOAN_ID
            WHERE a.ACTION = 'LOAN_EXIT_EVENT_READY' AND p.PAYMENT_STATUS = 'SUCCESS'
              AND q.QUOTE_STATUS = 'PAID' AND c.STATUS = 'COMPLETED' AND l.LOAN_STATUS = 'CLOSED'
              AND l.OUTSTANDING_AMOUNT = 0 AND l.CLOSURE_DATE IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM AUDIT_LOG sent WHERE sent.PAYMENT_ID = p.PAYMENT_ID
                  AND sent.ACTION = 'LOAN_EXIT_EVENT_PUBLISHED')
            ORDER BY a.LOG_ID FETCH FIRST ? ROWS ONLY
            """, (rs, row) -> {
                String details = rs.getString("DETAILS");
                String[] fields = details.split(";occurredAt=", 2);
                if (fields.length != 2 || !fields[0].startsWith("eventId=")) throw new IllegalStateException("Invalid completion event identity.");
                return new LoanExitCompletedEvent(fields[0].substring(8), LoanExitCompletedEvent.TYPE,
                    LoanExitCompletedEvent.VERSION, rs.getLong("LOAN_ID"), rs.getLong("EXIT_REQUEST_ID"),
                    rs.getLong("QUOTE_ID"), rs.getLong("PAYMENT_ID"), "COOLING_OFF", Instant.parse(fields[1]));
            }, batchSize);
        pending.forEach(this::publish);
    }
}
