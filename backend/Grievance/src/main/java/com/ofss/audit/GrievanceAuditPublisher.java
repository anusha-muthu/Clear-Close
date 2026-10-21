package com.ofss.audit;

import com.ofss.dto.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "clearclose.audit.enabled", havingValue = "true")
public class GrievanceAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(GrievanceAuditPublisher.class);
    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final String topic;

    public GrievanceAuditPublisher(
            KafkaTemplate<String, AuditEvent> kafkaTemplate,
            @Value("${clearclose.audit.topic:clearclose.audit.events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AuditEvent event) {
        try {
            kafkaTemplate.send(topic, "GRIEVANCE:" + event.entityId(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Audit publication failed after commit for grievance {} ({})",
                                    event.entityId(), event.action(), failure);
                        } else {
                            log.info("Published audit event {} for grievance {}",
                                    event.action(), event.entityId());
                        }
                    });
        } catch (Exception failure) {
            log.error("Audit publication failed after commit for grievance {} ({})",
                    event.entityId(), event.action(), failure);
        }
    }
}
