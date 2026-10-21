package com.clearclose.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp; // Added for tracking updates
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "COLLATERAL_RELEASE_REQUESTS")
public class CollateralReleaseRequests {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "REQUEST_ID")
	private Long requestId;

	@Column(name = "COLLATERAL_ID", nullable = false)
	private Long collateralId;

	@Column(name = "LOAN_ACCOUNT_ID", nullable = false)
	private Long loanAccountId;

	@Column(name = "REQUESTED_BY")
	private Long requestedBy;

	@Column(name = "ASSIGNED_TO")
	private Long assignedTo;

	@Column(name = "STATUS", length = 30)
	private String status = "PENDING";

	@Column(name = "REASON", length = 500)
	private String reason;

	@Column(name = "EXPECTED_RELEASE_DATE")
	private LocalDateTime expectedReleaseDate;

	@Column(name = "RELEASED_AT")
	private LocalDateTime releasedAt;

	@Column(name = "RELEASE_REFERENCE", length = 50)
	private String releaseReference;

	@Column(name = "CREATED_AT", nullable = false, updatable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;

	@Column(name = "UPDATED_AT", nullable = false) // Fixed duplicate column mapping
	@UpdateTimestamp // Changed from @CreationTimestamp to @UpdateTimestamp
	private LocalDateTime updatedAt;

	// Default Constructor
	public CollateralReleaseRequests() {
		super();
	}

	// Parameterized Constructor
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

	// Getters and Setters (Fixed Typos to cleanly match fields)
	public Long getRequestId() { return requestId; }
	public void setRequestId(Long requestId) { this.requestId = requestId; }

	public Long getCollateralId() { return collateralId; }
	public void setCollateralId(Long collateralId) { this.collateralId = collateralId; }

	public Long getLoanAccountId() { return loanAccountId; }
	public void setLoanAccountId(Long loanAccountId) { this.loanAccountId = loanAccountId; }

	public Long getRequestedBy() { return requestedBy; }
	public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }

	public Long getAssignedTo() { return assignedTo; }
	public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }

	public LocalDateTime getExpectedReleaseDate() { return expectedReleaseDate; }
	public void setExpectedReleaseDate(LocalDateTime expectedReleaseDate) { this.expectedReleaseDate = expectedReleaseDate; }

	public LocalDateTime getReleasedAt() { return releasedAt; }
	public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }

	public String getReleaseReference() { return releaseReference; }
	public void setReleaseReference(String releaseReference) { this.releaseReference = releaseReference; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

	@Override
	public String toString() {
		return "CollateralReleaseRequests [requestId=" + requestId + ", collateralId=" + collateralId
				+ ", loanAccountId=" + loanAccountId + ", requestedBy=" + requestedBy + ", assignedTo=" + assignedTo
				+ ", status=" + status + ", reason=" + reason + ", expectedReleaseDate=" + expectedReleaseDate
				+ ", releasedAt=" + releasedAt + ", releaseReference=" + releaseReference + ", createdAt=" + createdAt
				+ ", updatedAt=" + updatedAt + "]";
	}
}
