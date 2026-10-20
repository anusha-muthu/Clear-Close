package com.clearclose.consumer;

import com.clearclose.dto.AuditEvent;
import com.clearclose.service.AuditLogService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

    private final AuditLogService auditLogService;

    public AuditEventConsumer(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
        topics = "clearclose.audit.events",
        groupId = "clearclose-audit-group"
    )
    public void consume(AuditEvent event) {
        auditLogService.recordEvent(event);
    }
}