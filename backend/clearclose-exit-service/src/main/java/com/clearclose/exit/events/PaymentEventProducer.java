package com.clearclose.exit.events;

import java.time.Instant;
import java.util.UUID;

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

@Component
public class PaymentEventProducer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public PaymentEventProducer(JdbcTemplate jdbc, ApplicationEventPublisher events,
            KafkaTemplate<String, Object> kafka,
            @Value("${clearclose.events.payment-topic}") String topic) {
        this.jdbc = jdbc;
        this.events = events;
        this.kafka = kafka;
        this.topic = topic;
    }

    /** Called only for the existing INITIATED -> SUCCESS database transition. */
    public void recordSucceeded(long paymentId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Payment event must be prepared inside the payment transaction.");
        }
        String eventId = UUID.randomUUID().toString();
        var records = jdbc.query("""
            SELECT p.PAYMENT_ID, p.QUOTE_ID, p.AMOUNT, q.LOAN_ID, q.EXIT_REQUEST_ID,
                   TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),
                       'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"') AS EVENT_TIME
            FROM PAYMENT p
            JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID
            WHERE p.PAYMENT_ID = ? AND p.PAYMENT_STATUS = 'SUCCESS'
              AND p.RAZORPAY_PAYMENT_ID IS NOT NULL AND p.CONFIRMED_AT IS NOT NULL
              AND q.QUOTE_TYPE = 'COOLING_OFF' AND p.AMOUNT = q.TOTAL_AMOUNT
            """, (rs, row) -> new PaymentEvent(eventId, PaymentEvent.TYPE, PaymentEvent.VERSION,
                rs.getLong("PAYMENT_ID"), rs.getLong("QUOTE_ID"), rs.getLong("LOAN_ID"),
                rs.getLong("EXIT_REQUEST_ID"), rs.getBigDecimal("AMOUNT"), "INR",
                Instant.parse(rs.getString("EVENT_TIME"))), paymentId);
        if (records.size() != 1) throw new IllegalStateException("Verified payment event data is missing.");
        PaymentEvent event = records.getFirst();
        // Durable identity for inspection/replay, not an automatically dispatched outbox.
        jdbc.update("""
            INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS,
                                   PAYMENT_ID, QUOTE_ID, EXIT_REQUEST_ID)
            SELECT l.BORROWER_ID, ?, 'PAYMENT', 'PAYMENT_EVENT_READY', ?, ?, ?, ?
            FROM LOAN_ACCOUNT l WHERE l.LOAN_ID = ?
            """, paymentId, event.auditIdentity(), paymentId, event.quoteId(), event.exitRequestId(), event.loanId());
        events.publishEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentEvent event) {
        try {
            kafka.send(topic, event.quoteId().toString(), event).whenComplete((result, failure) -> {
                if (failure == null) {
                    log.info("Published PaymentSucceeded eventId={} paymentId={} quoteId={}",
                        event.eventId(), event.paymentId(), event.quoteId());
                } else {
                    failed(event);
                }
            });
        } catch (RuntimeException failure) {
            // Oracle already committed. Do not turn a successful payment into an API failure.
            failed(event);
        }
    }

    private void failed(PaymentEvent event) {
        log.error("PaymentSucceeded publication failed after bounded Kafka retries: eventId={} paymentId={} quoteId={}. "
            + "Payment remains SUCCESS. Replay PAYMENT_EVENT_READY using kafka-verification.sql.",
            event.eventId(), event.paymentId(), event.quoteId());
    }
}
