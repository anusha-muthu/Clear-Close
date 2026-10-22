
package com.clearclose.noc.dto;

import com.clearclose.noc.entity.Noc;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record NocResponse(
    Long nocId,
    Long loanId,
    String certificateNumber,
    String status,
    Integer versionNumber,
    LocalDate issueDate,
    LocalDateTime generatedAt,
    String remarks
) {
    public static NocResponse from(Noc noc) {
        return new NocResponse(
            noc.getNocId(),
            noc.getLoanId(),
            noc.getCertificateNumber(),
            noc.getNocStatus(),
            noc.getVersionNumber(),
            noc.getIssueDate(),
            noc.getGeneratedAt(),
            noc.getRemarks()
        );
    }
}
