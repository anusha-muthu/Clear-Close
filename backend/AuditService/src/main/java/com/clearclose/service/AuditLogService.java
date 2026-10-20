package com.clearclose.service;

import com.clearclose.dto.AuditEvent;
import com.clearclose.entity.AuditLog;
import com.clearclose.repository.AuditLogRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog recordEvent(AuditEvent event) {
        AuditLog log = new AuditLog();

        log.setUserId(event.userId());
        log.setOfficerId(event.officerId());
        log.setEntityId(event.entityId());
        log.setEntityType(event.entityType());
        log.setAction(event.action());
        log.setActionTime(event.actionTime() != null
                ? event.actionTime()
                : LocalDateTime.now(ZoneOffset.UTC));
        log.setPaymentId(event.paymentId());
        log.setQuoteId(event.quoteId());
        log.setExitRequestId(event.exitRequestId());
        log.setClosureRequestId(event.closureRequestId());
        log.setNocId(event.nocId());
        log.setGrievanceId(event.grievanceId());
        log.setReleaseRequestId(event.releaseRequestId());

        return repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getEntityHistory(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByActionTimeDesc(
                entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getUserHistory(Long userId) {
        return repository.findByUserIdOrderByActionTimeDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAllAuditLogs() {
        return repository.findAll();
    }
}