package com.ofss.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
public class Grievances {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long grievanceId;
	private Long loanAccountId;
	private Long borrowerId;
	private String description;
	private String category;
	private String status;
	private Long assignedTo;
	private String resolutionRemarks;
	@Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "UPDATED_AT", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;
	
	@PrePersist
    protected void onCreate() {
        // 1. Force current timestamps if null or during initial creation
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();

        // 2. Apply default placeholder values specifically for the 'OPEN' status
        if ("OPEN".equalsIgnoreCase(this.status)) {
            if (this.assignedTo == null) {
                this.assignedTo = 0L; // Use 0 or a specific default system User ID
            }
            if (this.resolutionRemarks == null || this.resolutionRemarks.trim().isEmpty()) {
                this.resolutionRemarks = "Grievance is currently open and awaiting review.";
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

	public Grievances() {
		super();
		// TODO Auto-generated constructor stub
	}
	public Grievances(Long grievanceId, Long loanAccountId, Long borrowerId, String description,
			String category, String status, Long assignedTo, String resolutionRemarks, LocalDateTime createdAt,
			LocalDateTime updatedAt) {
		super();
		this.grievanceId = grievanceId;
		this.loanAccountId = loanAccountId;
		this.borrowerId = borrowerId;
		this.description = description;
		this.category = category;
		this.status = status;
		this.assignedTo = assignedTo;
		this.resolutionRemarks = resolutionRemarks;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	public Long getGrievanceId() {
		return grievanceId;
	}
	public void setGrievanceId(Long grievanceId) {
		this.grievanceId = grievanceId;
	}
	public Long getLoanAccountId() {
		return loanAccountId;
	}
	public void setLoanAccountId(Long loanAccountId) {
		this.loanAccountId = loanAccountId;
	}
	public Long getBorrowerId() {
		return borrowerId;
	}
	public void setBorrowerId(Long borrowerId) {
		this.borrowerId = borrowerId;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public Long getAssignedTo() {
		return assignedTo;
	}
	public void setAssignedTo(Long assignedTo) {
		this.assignedTo = assignedTo;
	}
	public String getResolutionRemarks() {
		return resolutionRemarks;
	}
	public void setResolutionRemarks(String resolutionRemarks) {
		this.resolutionRemarks = resolutionRemarks;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	@Override
	public String toString() {
		return "GrievancesTable [grievanceId=" + grievanceId + ", loanAccountId=" + loanAccountId + ", borrowerId="
				+ borrowerId + ", description=" + description + ", category=" + category + ", status=" + status
				+ ", assignedTo=" + assignedTo + ", resolutionRemarks=" + resolutionRemarks + ", createdAt=" + createdAt
				+ ", updatedAt=" + updatedAt + "]";
	}
	
	


}
