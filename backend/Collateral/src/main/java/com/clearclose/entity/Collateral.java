package com.clearclose.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "COLLATERAL")
public class Collateral {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "COLLATERAL_ID")
	private Long collateralId;
	
	@NotNull(message = "Loan account ID is required")
	@Column(name = "LOAN_ACCOUNT_ID", nullable = false)
	private Long loanAccountId;
	
	@NotBlank(message = "Collateral Type is required")
	@Column(name = "COLLATERAL_TYPE", nullable = false, length = 30)
	private String collateralType;
	
	@Column(name = "DESCRIPTION", length = 500)
	private String description;
	
	@NotBlank(message = "File name is required")
	@Column(name = "FILE_NAME", nullable = false, length = 30)
	private String fileName;
	
	@NotBlank(message = "Storage path is required")
	@Column(name = "STORAGE_PATH", nullable = false, length = 50)
	private String storagePath;
	
	@Column(name = "CUSTODIAN", length = 100)
	private String custodian;
	
	@Column(name = "RELEASE_STATUS", nullable = false, length = 30)
	private String releaseStatus = "NOT_ELIGIBLE"; // Match the default check constraint
	
	@Column(name = "CREATED_AT", nullable = false, updatable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;

	// Default Constructor
	public Collateral() {
		super();
	}

	// Parameterized Constructor
	public Collateral(Long collateralId, Long loanAccountId, String collateralType, String description,
			String fileName, String storagePath, String custodian, String releaseStatus, LocalDateTime createdAt) {
		super();
		this.collateralId = collateralId;
		this.loanAccountId = loanAccountId;
		this.collateralType = collateralType;
		this.description = description;
		this.fileName = fileName;
		this.storagePath = storagePath;
		this.custodian = custodian;
		this.releaseStatus = releaseStatus;
		this.createdAt = createdAt;
	}

	// Getters and Setters
	public Long getCollateralId() { return collateralId; }
	public void setCollateralId(Long collateralId) { this.collateralId = collateralId; }

	public Long getLoanAccountId() { return loanAccountId; }
	public void setLoanAccountId(Long loanAccountId) { this.loanAccountId = loanAccountId; }

	public String getCollateralType() { return collateralType; }
	public void setCollateralType(String collateralType) { this.collateralType = collateralType; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }

	public String getStoragePath() { return storagePath; }
	public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

	public String getCustodian() { return custodian; }
	public void setCustodian(String custodian) { this.custodian = custodian; }

	public String getReleaseStatus() { return releaseStatus; }
	public void setReleaseStatus(String releaseStatus) { this.releaseStatus = releaseStatus; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	@Override
	public String toString() {
		return "Collateral [collateralId=" + collateralId + ", loanAccountId=" + loanAccountId + ", collateralType="
				+ collateralType + ", description=" + description + ", fileName=" + fileName + ", storagePath="
				+ storagePath + ", custodian=" + custodian + ", releaseStatus=" + releaseStatus + ", createdAt="
				+ createdAt + "]";
	}
}
