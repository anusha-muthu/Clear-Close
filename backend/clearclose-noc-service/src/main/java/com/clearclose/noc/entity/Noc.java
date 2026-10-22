
package com.clearclose.noc.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "NOC")
public class Noc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOC_ID")
    private Long nocId;

    @Column(name = "LOAN_ID", nullable = false)
    private Long loanId;

    @Column(name = "CERTIFICATE_NUMBER", nullable = false, unique = true, length = 100)
    private String certificateNumber;

    @Column(name = "ISSUE_DATE", nullable = false)
    private LocalDate issueDate;

    @Column(name = "FILE_PATH", length = 500)
    private String filePath;

    @Column(name = "NOC_STATUS", nullable = false, length = 30)
    private String nocStatus;

    @Column(name = "VERSION_NUMBER", nullable = false)
    private Integer versionNumber;

    @Column(name = "GENERATED_AT")
    private LocalDateTime generatedAt;

    @Column(name = "ISSUED_BY")
    private Long issuedBy;

    @Column(name = "REMARKS", length = 1000)
    private String remarks;

    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (issueDate == null) issueDate = LocalDate.now();
        if (nocStatus == null) nocStatus = "GENERATION_PENDING";
        if (versionNumber == null) versionNumber = 1;
    }

    @PreUpdate
    public void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getNocId() { return nocId; }
    public void setNocId(Long nocId) { this.nocId = nocId; }

    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }

    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String value) {
        this.certificateNumber = value;
    }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate value) { this.issueDate = value; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String value) { this.filePath = value; }

    public String getNocStatus() { return nocStatus; }
    public void setNocStatus(String value) { this.nocStatus = value; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer value) {
        this.versionNumber = value;
    }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime value) {
        this.generatedAt = value;
    }

    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long value) { this.issuedBy = value; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String value) { this.remarks = value; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String value) { this.fileHash = value; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
