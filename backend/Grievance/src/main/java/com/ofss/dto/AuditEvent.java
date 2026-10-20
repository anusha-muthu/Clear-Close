package com.ofss.dto;

import java.time.LocalDateTime;

public record AuditEvent (
		Long logId,
		Long userId,Long officerId,
		Long entityId, String entityType,
	String action,LocalDateTime actionTime,
	Long paymentId,Long quoteId,
	Long exitRequestId, Long closureRequestId,
	Long nocId,Long grievanceId,
	Long releaseRequestId
) {}
