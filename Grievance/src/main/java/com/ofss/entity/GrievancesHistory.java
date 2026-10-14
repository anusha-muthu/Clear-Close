package com.ofss.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class GrievancesHistory {
	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	private Long historyId;
	private Long grievanceId; 
	private String oldStatus;
	private String newStatus;
	private String actionRemarks;
	private Long actionBy;
	@Column(name = "ACTION_AT", nullable = false, insertable = false, updatable = false)
	private LocalDateTime actionAt;
	public GrievancesHistory() {
		super();
		// TODO Auto-generated constructor stub
	}
	public GrievancesHistory(Long historyId, Long grievanceId, String oldStatus, String newStatus,
			String actionRemarks, Long actionBy, LocalDateTime actionAt) {
		super();
		this.historyId = historyId;
		this.grievanceId = grievanceId;
		this.oldStatus = oldStatus;
		this.newStatus = newStatus;
		this.actionRemarks = actionRemarks;
		this.actionBy = actionBy;
		this.actionAt = actionAt;
	}
	public Long getHistoryId() {
		return historyId;
	}
	public void setHistoryId(Long historyId) {
		this.historyId = historyId;
	}
	public Long getGrievanceId() {
		return grievanceId;
	}
	public void setGrievanceId(Long grievanceId) {
		this.grievanceId = grievanceId;
	}
	public String getOldStatus() {
		return oldStatus;
	}
	public void setOldStatus(String oldStatus) {
		this.oldStatus = oldStatus;
	}
	public String getNewStatus() {
		return newStatus;
	}
	public void setNewStatus(String newStatus) {
		this.newStatus = newStatus;
	}
	public String getActionRemarks() {
		return actionRemarks;
	}
	public void setActionRemarks(String actionRemarks) {
		this.actionRemarks = actionRemarks;
	}
	public Long getActionBy() {
		return actionBy;
	}
	public void setActionBy(Long actionBy) {
		this.actionBy = actionBy;
	}
	public LocalDateTime getActionAt() {
		return actionAt;
	}
	public void setActionAt(LocalDateTime actionAt) {
		this.actionAt = actionAt;
	}
	@Override
	public String toString() {
		return "GrievancesHistoryTable [historyId=" + historyId + ", grievanceId=" + grievanceId + ", oldStatus="
				+ oldStatus + ", newStatus=" + newStatus + ", actionRemarks=" + actionRemarks + ", actionBy=" + actionBy
				+ ", actionAt=" + actionAt + "]";
	}
	
	
}
