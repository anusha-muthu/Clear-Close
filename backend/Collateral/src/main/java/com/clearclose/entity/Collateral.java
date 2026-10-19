package com.clearclose.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
public class Collateral {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long collateralId;
	
	@NotNull(message="Loan account ID is required")
	private Long loanAccountId;
	
	@NotBlank(message = "Collateral Type is required")
	@Column(name = "COLLATERAL_TYPE", nullable = false)
	private String collateralType;
	
	
	private String description;
	private String custodian;
	private String releaseStatus;
	@Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;

	public Collateral() {
		super();
		// TODO Auto-generated constructor stub
	}
	public Collateral(Long collateralId, Long loanAccountId, String collateralType, String description,
			String custodian, String releaseStatus, LocalDateTime createdAt) {
		super();
		this.collateralId = collateralId;
		this.loanAccountId = loanAccountId;
		this.collateralType = collateralType;
		this.description = description;
		this.custodian = custodian;
		this.releaseStatus = releaseStatus;
		this.createdAt = createdAt;
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
	public String getCollateralType() {
		return collateralType;
	}
	public void setCollateralType(String collateralType) {
		this.collateralType = collateralType;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getCustodian() {
		return custodian;
	}
	public void setCustodian(String custodian) {
		this.custodian = custodian;
	}
	public String getReleaseStatus() {
		return releaseStatus;
	}
	public void setReleaseStatus(String releaseStatus) {
		this.releaseStatus = releaseStatus;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	@Override
	public String toString() {
		return "Collateral [collateralId=" + collateralId + ", loanAccountId=" + loanAccountId + ", collateralType="
				+ collateralType + ", description=" + description + ", custodian=" + custodian + ", releaseStatus="
				+ releaseStatus + ", createdAt=" + createdAt + "]";
	}

	
}
