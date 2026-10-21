package com.clearclose.controller;

import com.clearclose.entity.AuditLog;
import com.clearclose.service.AuditLogService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // View audit history for a particular entity
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOAN_CLOSURE_OFFICER')")
    public List<AuditLog> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable Long entityId) {

        return auditLogService.getEntityHistory(
                entityType, entityId);
    }

    // View actions performed by a particular user
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> getUserHistory(
            @PathVariable Long userId) {

        return auditLogService.getUserHistory(userId);
    }

    // View all audit records
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> getAllAuditLogs() {
        return auditLogService.getAllAuditLogs();
    }
}
