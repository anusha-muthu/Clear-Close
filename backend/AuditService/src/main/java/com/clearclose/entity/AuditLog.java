package com.clearclose.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class AuditLog {
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long logId;
	private Long userId;
	private Long officerId;
	private Long entityId;
	private String entityType;
	private String action;
	private LocalDateTime actionTime;
	private Long paymentId;
	private Long quoteId;
	private Long exitRequestId;
	private Long closureRequestId;
	private Long nocId;
	private Long grievanceId;
	private Long releaseRequestId;
	public AuditLog() {
		super();
		// TODO Auto-generated constructor stub
	}
	public AuditLog(Long logId, Long userId, Long officerId, Long entityId, String entityType, String action,
			LocalDateTime actionTime, Long paymentId, Long quoteId, Long exitRequestId, Long closureRequestId,
			Long nocId, Long grievanceId, Long releaseRequestId) {
		super();
		this.logId = logId;
		this.userId = userId;
		this.officerId = officerId;
		this.entityId = entityId;
		this.entityType = entityType;
		this.action = action;
		this.actionTime = actionTime;
		this.paymentId = paymentId;
		this.quoteId = quoteId;
		this.exitRequestId = exitRequestId;
		this.closureRequestId = closureRequestId;
		this.nocId = nocId;
		this.grievanceId = grievanceId;
		this.releaseRequestId = releaseRequestId;
	}
	public Long getLogId() {
		return logId;
	}
	public void setLogId(Long logId) {
		this.logId = logId;
	}
	public Long getUserId() {
		return userId;
	}
	public void setUserId(Long userId) {
		this.userId = userId;
	}
	public Long getOfficerId() {
		return officerId;
	}
	public void setOfficerId(Long officerId) {
		this.officerId = officerId;
	}
	public Long getEntityId() {
		return entityId;
	}
	public void setEntityId(Long entityId) {
		this.entityId = entityId;
	}
	public String getEntityType() {
		return entityType;
	}
	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public LocalDateTime getActionTime() {
		return actionTime;
	}
	public void setActionTime(LocalDateTime actionTime) {
		this.actionTime = actionTime;
	}
	public Long getPaymentId() {
		return paymentId;
	}
	public void setPaymentId(Long paymentId) {
		this.paymentId = paymentId;
	}
	public Long getQuoteId() {
		return quoteId;
	}
	public void setQuoteId(Long quoteId) {
		this.quoteId = quoteId;
	}
	public Long getExitRequestId() {
		return exitRequestId;
	}
	public void setExitRequestId(Long exitRequestId) {
		this.exitRequestId = exitRequestId;
	}
	public Long getClosureRequestId() {
		return closureRequestId;
	}
	public void setClosureRequestId(Long closureRequestId) {
		this.closureRequestId = closureRequestId;
	}
	public Long getNocId() {
		return nocId;
	}
	public void setNocId(Long nocId) {
		this.nocId = nocId;
	}
	public Long getGrievanceId() {
		return grievanceId;
	}
	public void setGrievanceId(Long grievanceId) {
		this.grievanceId = grievanceId;
	}
	public Long getReleaseRequestId() {
		return releaseRequestId;
	}
	public void setReleaseRequestId(Long releaseRequestId) {
		this.releaseRequestId = releaseRequestId;
	}
	
	

}
