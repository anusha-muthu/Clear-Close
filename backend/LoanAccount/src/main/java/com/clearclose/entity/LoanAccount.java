package com.clearclose.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class LoanAccount {
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long loanId;
	private Long borrowerId;
	private Long ruleId;
	private String loanReference;
	private BigDecimal principalAmount;
	private BigDecimal outstandingAmount;
	private BigDecimal interestRate;
	private Integer tenureMonths;
	private LocalDateTime disbursementDate;
	private LocalDateTime maturityDate;
	private String loanStatus;
	private LocalDateTime closureDate;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	public LoanAccount() {
		super();
		// TODO Auto-generated constructor stub
	}
	public LoanAccount(Long loanId, Long borrowerId, Long ruleId, String loanReference, BigDecimal principalAmount,
			BigDecimal outstandingAmount, BigDecimal interestRate, Integer tenureMonths,
			LocalDateTime disbursementDate, LocalDateTime maturityDate, String loanStatus, LocalDateTime closureDate,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		super();
		this.loanId = loanId;
		this.borrowerId = borrowerId;
		this.ruleId = ruleId;
		this.loanReference = loanReference;
		this.principalAmount = principalAmount;
		this.outstandingAmount = outstandingAmount;
		this.interestRate = interestRate;
		this.tenureMonths = tenureMonths;
		this.disbursementDate = disbursementDate;
		this.maturityDate = maturityDate;
		this.loanStatus = loanStatus;
		this.closureDate = closureDate;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	public Long getLoanId() {
		return loanId;
	}
	public void setLoanId(Long loanID) {
		this.loanId = loanID;
	}
	public Long getBorrowerId() {
		return borrowerId;
	}
	public void setBorrowerId(Long borrowerId) {
		this.borrowerId = borrowerId;
	}
	public Long getRuleId() {
		return ruleId;
	}
	public void setRuleId(Long ruleId) {
		this.ruleId = ruleId;
	}
	public String getLoanReference() {
		return loanReference;
	}
	public void setLoanReference(String loanReference) {
		this.loanReference = loanReference;
	}
	public BigDecimal getPrincipalAmount() {
		return principalAmount;
	}
	public void setPrincipalAmount(BigDecimal principalAmount) {
		this.principalAmount = principalAmount;
	}
	public BigDecimal getOutstandingAmount() {
		return outstandingAmount;
	}
	public void setOutstandingAmount(BigDecimal outstandingAmount) {
		this.outstandingAmount = outstandingAmount;
	}
	public BigDecimal getInterestRate() {
		return interestRate;
	}
	public void setInterestRate(BigDecimal interestRate) {
		this.interestRate = interestRate;
	}
	public Integer getTentureMonths() {
		return tenureMonths;
	}
	public void setTentureMonths(Integer tentureMonths) {
		this.tenureMonths = tentureMonths;
	}
	public LocalDateTime getDisbursementDate() {
		return disbursementDate;
	}
	public void setDisbursementDate(LocalDateTime disbursementDate) {
		this.disbursementDate = disbursementDate;
	}
	public LocalDateTime getMaturityDate() {
		return maturityDate;
	}
	public void setMaturityDate(LocalDateTime maturityDate) {
		this.maturityDate = maturityDate;
	}
	public String getLoanStatus() {
		return loanStatus;
	}
	public void setLoanStatus(String loanStatus) {
		this.loanStatus = loanStatus;
	}
	public LocalDateTime getClosureDate() {
		return closureDate;
	}
	public void setClosureDate(LocalDateTime closureDate) {
		this.closureDate = closureDate;
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
		return "LoanAccount [loanID=" + loanId + ", borrowerId=" + borrowerId + ", ruleId=" + ruleId
				+ ", loanReference=" + loanReference + ", principalAmount=" + principalAmount + ", outstandingAmount="
				+ outstandingAmount + ", interestRate=" + interestRate + ", tentureMonths=" + tenureMonths
				+ ", disbursementDate=" + disbursementDate + ", maturityDate=" + maturityDate + ", loanStatus="
				+ loanStatus + ", closureDate=" + closureDate + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt
				+ "]";
	}
	
	
	
}
