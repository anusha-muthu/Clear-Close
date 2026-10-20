package com.clearclose.repository;

import com.clearclose.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByActionTimeDesc(
            String entityType, Long entityId);

    List<AuditLog> findByUserIdOrderByActionTimeDesc(Long userId);
}