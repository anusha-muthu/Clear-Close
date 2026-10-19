package com.clearclose.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class CollateralReleaseRequests {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long requestId;
	private Long collateralId;
	private Long loanAccountId;
	private Long requestedBy;
	private Long assignedTo;
	private String status;
	private String reason;
	private LocalDateTime expectedReleaseDate;
	private LocalDateTime releasedAt;
	private String releaseReference;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	
	
	public CollateralReleaseRequests() {
		super();
		// TODO Auto-generated constructor stub
	}


	public CollateralReleaseRequests(Long requestId, Long collateralId, Long loanAccountId, Long requestedBy,
			Long assignedTo, String status, String reason, LocalDateTime expectedReleaseDate, LocalDateTime releasedAt,
			String releaseReference, LocalDateTime createdAt, LocalDateTime updatedAt) {
		super();
		this.requestId = requestId;
		this.collateralId = collateralId;
		this.loanAccountId = loanAccountId;
		this.requestedBy = requestedBy;
		this.assignedTo = assignedTo;
		this.status = status;
		this.reason = reason;
		this.expectedReleaseDate = expectedReleaseDate;
		this.releasedAt = releasedAt;
		this.releaseReference = releaseReference;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}


	public Long getRequestId() {
		return requestId;
	}


	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}


	public Long getCollateralId() {
		return collateralId;
	}


	public void setCollateralId(Long collateralId) {
		this.collateralId = collateralId;
	}


	public Long getLoanAccountId() {
		return loanAccountId;
	}


	public void setLoanAccountId(Long loanAccountId) {
		this.loanAccountId = loanAccountId;
	}


	public Long getRequestBy() {
		return requestedBy;
	}


	public void setRequestBy(Long requestBy) {
		this.requestedBy = requestBy;
	}


	public Long getAssignedTo() {
		return assignedTo;
	}


	public void setAssignedTo(Long assignedTo) {
		this.assignedTo = assignedTo;
	}


	public String getStatus() {
		return status;
	}


	public void setStatus(String status) {
		this.status = status;
	}


	public String getReason() {
		return reason;
	}


	public void setReason(String reason) {
		this.reason = reason;
	}


	public LocalDateTime getExceptedReleaseDate() {
		return expectedReleaseDate;
	}


	public void setExceptedReleaseDate(LocalDateTime exceptedReleaseDate) {
		this.expectedReleaseDate = exceptedReleaseDate;
	}


	public LocalDateTime getReleaseAt() {
		return releasedAt;
	}


	public void setReleaseAt(LocalDateTime releaseAt) {
		this.releasedAt = releaseAt;
	}


	public String getReleaseReference() {
		return releaseReference;
	}


	public void setReleaseReference(String releaseReference) {
		this.releaseReference = releaseReference;
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
		return "CollateralReleaseRequests [requestId=" + requestId + ", collateralId=" + collateralId
				+ ", loanAccountId=" + loanAccountId + ", requestBy=" + requestedBy + ", assignedTo=" + assignedTo
				+ ", status=" + status + ", reason=" + reason + ", exceptedReleaseDate=" + expectedReleaseDate
				+ ", releaseAt=" + releasedAt + ", releaseReference=" + releaseReference + ", createdAt=" + createdAt
				+ ", updatedAt=" + updatedAt + "]";
	}
	
	

}
