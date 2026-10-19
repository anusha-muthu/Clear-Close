package com.clearclose.dto;

import java.math.BigDecimal;

public class LoanAccountResponse {

    private Long loanId;
    private Long borrowerId;
    private Long ruleId;
    private String loanStatus;
    private BigDecimal outstandingAmount;

    public LoanAccountResponse() {
    }

    public LoanAccountResponse(Long loanId, Long borrowerId, Long ruleId,
                               String loanStatus,
                               BigDecimal outstandingAmount) {
        this.loanId = loanId;
        this.borrowerId = borrowerId;
        this.ruleId = ruleId;
        this.loanStatus = loanStatus;
        this.outstandingAmount = outstandingAmount;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
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

    public String getLoanStatus() {
        return loanStatus;
    }

    public void setLoanStatus(String loanStatus) {
        this.loanStatus = loanStatus;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }
}