package com.clearclose.exit.events;

import com.clearclose.exit.completion.FinalExitCompletionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final PaymentConfirmationService confirmation;
    private final FinalExitCompletionService completion;

    public PaymentEventConsumer(PaymentConfirmationService confirmation, FinalExitCompletionService completion) {
        this.confirmation = confirmation;
        this.completion = completion;
    }

    @KafkaListener(id = "exitPaymentConfirmation",
        topics = "${clearclose.events.payment-topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "paymentKafkaListenerContainerFactory",
        autoStartup = "${clearclose.events.listener-enabled}")
    public void consume(ConsumerRecord<String, PaymentEvent> record) {
        // The service transaction commits before this method returns and the offset is acknowledged.
        try {
            boolean changed = confirmation.confirm(record.key(), record.value());
            // Call even for redelivery: confirmation may have committed before an earlier closure failure.
            boolean completed = completion.complete(record.value().paymentId());
            log.info("PaymentSucceeded eventId={} paymentId={} result={}",
                record.value().eventId(), record.value().paymentId(), completed ? "EXIT_COMPLETED"
                    : changed ? "CONFIRMED" : "DUPLICATE_IGNORED");
        } catch (RuntimeException failure) {
            String id = record.value() == null ? null : record.value().eventId();
            String safeId = id != null && id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                ? id : "INVALID";
            log.warn("PaymentSucceeded eventId={} result=RETRY_OR_DLT partition={} offset={} errorType={}",
                safeId, record.partition(), record.offset(), failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
