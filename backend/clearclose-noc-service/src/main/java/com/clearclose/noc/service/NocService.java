
package com.clearclose.noc.service;

import com.clearclose.noc.dto.NocEligibilityResponse;
import com.clearclose.noc.dto.NocResponse;
import com.clearclose.noc.entity.Noc;
import com.clearclose.noc.entity.NocAudit;
import com.clearclose.noc.repository.NocAuditRepository;
import com.clearclose.noc.repository.NocRepository;
import com.clearclose.noc.security.NocAccess;
import com.clearclose.noc.security.NocAccess.Actor;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class NocService {

    private final NocRepository nocRepository;
    private final NocAuditRepository auditRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NocAccess access;

    @Value("${noc.storage.directory:./generated-nocs}")
    private String storageDirectory;

    @Value("${noc.lender-name:Clear Close Demo Bank}")
    private String lenderName;

    public NocService(
            NocRepository nocRepository,
            NocAuditRepository auditRepository,
            JdbcTemplate jdbcTemplate,
            NocAccess access) {

        this.nocRepository = nocRepository;
        this.auditRepository = auditRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.access = access;
    }

    private record LoanDetails(
            Long loanId,
            Long borrowerId,
            String borrowerName,
            String loanReference,
            String loanStatus,
            BigDecimal outstandingAmount,
            LocalDate closureDate
    ) {}

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, message);
    }

    private LoanDetails getLoan(Long loanId, boolean lock) {

        String sql = """
            SELECT
                L.LOAN_ID,
                L.BORROWER_ID,
                B.NAME AS BORROWER_NAME,
                L.LOAN_REFERENCE,
                L.LOAN_STATUS,
                L.OUTSTANDING_AMOUNT,
                L.CLOSURE_DATE
            FROM LOAN_ACCOUNT L
            JOIN BORROWERS B
                ON B.USER_ID = L.BORROWER_ID
            WHERE L.LOAN_ID = ?
            """;

        if (lock) {
            sql += " FOR UPDATE OF L.LOAN_ID";
        }

        List<LoanDetails> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new LoanDetails(
                        rs.getLong("LOAN_ID"),
                        rs.getLong("BORROWER_ID"),
                        rs.getString("BORROWER_NAME"),
                        rs.getString("LOAN_REFERENCE"),
                        rs.getString("LOAN_STATUS"),
                        rs.getBigDecimal("OUTSTANDING_AMOUNT"),
                        rs.getDate("CLOSURE_DATE") == null
                            ? null
                            : rs.getDate("CLOSURE_DATE")
                                .toLocalDate()
                ),
                loanId
        );

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Loan not found");
        }

        return results.get(0);
    }

    private void validateEligibility(LoanDetails loan) {

        if (!"CLOSED".equalsIgnoreCase(loan.loanStatus())) {
            throw conflict(
                "NOC cannot be generated: loan is not closed");
        }

        if (loan.outstandingAmount() == null
                || loan.outstandingAmount()
                    .compareTo(BigDecimal.ZERO) != 0) {

            throw conflict(
                "NOC cannot be generated: outstanding dues exist");
        }

        if (loan.closureDate() == null) {
            throw conflict(
                "NOC cannot be generated: closure date is missing");
        }
    }

    @Transactional(readOnly = true)
    public NocEligibilityResponse eligibility(Long loanId, Actor actor) {

        // Keep ownership failures outside the eligibility catch: return 403/404, not a data response.
        access.requireLoan(actor, loanId, false);

        try {
            LoanDetails loan = getLoan(loanId, false);
            validateEligibility(loan);

            return new NocEligibilityResponse(
                    loanId,
                    true,
                    "Loan eligible for demo NOC generation");

        } catch (ResponseStatusException ex) {
            return new NocEligibilityResponse(
                    loanId,
                    false,
                    ex.getReason());
        }
    }

    private Noc requireNoc(Long nocId) {
        if (nocId == null || nocId <= 0) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Valid NOC ID required.");
        return nocRepository.findById(nocId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "NOC not found"));
    }

    private String generateCertificateNumber() {
        return "NOC-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }

    private String calculateHash(byte[] data) throws Exception {
        byte[] hash = MessageDigest
                .getInstance("SHA-256")
                .digest(data);

        return HexFormat.of().formatHex(hash);
    }

    private Path storagePath() throws Exception {
        Path directory = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(directory);
        return directory;
    }

    private byte[] generatePdf(
            Noc noc,
            LoanDetails loan) throws Exception {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4);

        PdfWriter.getInstance(document, output);
        document.open();

        Font heading = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 19);

        Font normal = FontFactory.getFont(
                FontFactory.HELVETICA, 12);

        Paragraph title = new Paragraph(
                "NO OBJECTION CERTIFICATE", heading);

        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30);
        document.add(title);

        document.add(new Paragraph(
                "Lender: " + lenderName, normal));

        document.add(new Paragraph(
                "Certificate Number: "
                    + noc.getCertificateNumber(), normal));

        document.add(new Paragraph(
                "Borrower Name: "
                    + loan.borrowerName(), normal));

        document.add(new Paragraph(
                "Loan Reference: "
                    + loan.loanReference(), normal));

        document.add(new Paragraph(
                "Loan Account ID: "
                    + loan.loanId(), normal));

        document.add(new Paragraph(
                "Loan Closure Date: "
                    + loan.closureDate(), normal));

        document.add(new Paragraph(
                "Issue Date: "
                    + noc.getIssueDate(), normal));

        document.add(new Paragraph(
                "Certificate Version: "
                    + noc.getVersionNumber(), normal));

        document.add(new Paragraph(" "));

        Paragraph body = new Paragraph(
                "This is to certify that the above loan account "
                + "has been closed according to the records "
                + "available in the Clear Close demonstration "
                + "system. The recorded outstanding loan "
                + "balance is zero.",
                normal);

        body.setSpacingBefore(20);
        body.setSpacingAfter(25);

        document.add(body);

        document.add(new Paragraph(
                "Verification Reference: "
                    + noc.getCertificateNumber(), normal));

        document.add(new Paragraph(" "));

        document.add(new Paragraph(
                "This document uses synthetic demonstration "
                + "data and is not an official bank-issued "
                + "No Objection Certificate.",
                normal));

        document.close();

        return output.toByteArray();
    }

    private void audit(
            Noc noc,
            String action,
            Long actorId,
            String remarks) {

        auditRepository.save(new NocAudit(
                noc.getNocId(),
                action,
                actorId,
                remarks));
    }

    private NocResponse generateVersion(
            LoanDetails loan,
            int version,
            Long officerId,
            boolean reissue) {

        Noc noc = new Noc();

        noc.setLoanId(loan.loanId());
        noc.setCertificateNumber(
                generateCertificateNumber());
        noc.setVersionNumber(version);
        noc.setIssueDate(LocalDate.now());
        noc.setIssuedBy(officerId);
        noc.setNocStatus("GENERATION_PENDING");
        noc.setRemarks("Generating PDF");

        noc = nocRepository.saveAndFlush(noc);

        audit(noc, "GENERATION_STARTED",
                officerId, "PDF generation started");

        Path file = null;

        try {
            byte[] pdf = generatePdf(noc, loan);

            Path directory = storagePath();

            file = directory.resolve(
                    noc.getCertificateNumber() + ".pdf");

            Files.write(
                    file,
                    pdf,
                    StandardOpenOption.CREATE_NEW);

            noc.setFilePath(file.toString());
            noc.setFileHash(calculateHash(pdf));
            noc.setGeneratedAt(LocalDateTime.now());
            noc.setNocStatus("AVAILABLE");
            noc.setRemarks("PDF generated successfully");

            noc = nocRepository.saveAndFlush(noc);

            audit(
                    noc,
                    reissue ? "REISSUED" : "GENERATED",
                    officerId,
                    "Certificate ready for download");

            return NocResponse.from(noc);

        } catch (Exception ex) {

            // Do not return a success response on failure.
            // The failed record remains available for retry.
            noc.setNocStatus("FAILED");
            noc.setRemarks("PDF generation failed");

            nocRepository.save(noc);

            audit(noc, "FAILED", officerId,
                    "PDF generation failed");

            return NocResponse.from(noc);
        }
    }

    @Transactional
    public NocResponse generate(
            Long loanId,
            Actor actor) {

        access.requireLoan(actor, loanId, true);
        LoanDetails loan = getLoan(loanId, true);
        validateEligibility(loan);

        if (nocRepository
                .findTopByLoanIdOrderByVersionNumberDesc(loanId)
                .isPresent()) {

            throw conflict(
                "NOC already exists. Use reissue or retry.");
        }

        return generateVersion(
                loan, 1, actor.id(), false);
    }

    @Transactional
    public NocResponse reissue(
            Long loanId,
            Actor actor) {

        access.requireLoan(actor, loanId, true);
        LoanDetails loan = getLoan(loanId, true);
        validateEligibility(loan);

        Noc previous = nocRepository
                .findTopByLoanIdOrderByVersionNumberDesc(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existing NOC found"));

        if (!"AVAILABLE".equals(previous.getNocStatus())) {
            throw conflict(
                "Only available certificates can be reissued");
        }

        NocResponse replacement = generateVersion(
                loan,
                previous.getVersionNumber() + 1,
                actor.id(),
                true);

        if ("AVAILABLE".equals(replacement.status())) {
            previous.setNocStatus("REVOKED");
            previous.setRemarks(
                    "Superseded by a newer certificate");

            nocRepository.save(previous);

            audit(previous, "REVOKED",
                    actor.id(), "Certificate reissued");
        }

        return replacement;
    }

    @Transactional
    public NocResponse retry(
            Long nocId,
            Actor actor) {

        Noc noc = requireNoc(nocId);
        access.requireLoan(actor, noc.getLoanId(), true);

        LoanDetails loan = getLoan(noc.getLoanId(), true);
        validateEligibility(loan);

        if (!"FAILED".equals(noc.getNocStatus())
                && !"DELAYED".equals(noc.getNocStatus())) {

            throw conflict(
                "Only failed or delayed certificates can be retried");
        }

        try {
            byte[] pdf = generatePdf(noc, loan);

            Path file = storagePath().resolve(
                    noc.getCertificateNumber() + ".pdf");

            Files.write(
                    file,
                    pdf,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            noc.setFilePath(file.toString());
            noc.setFileHash(calculateHash(pdf));
            noc.setGeneratedAt(LocalDateTime.now());
            noc.setNocStatus("AVAILABLE");
            noc.setRemarks("PDF generated after retry");

            noc = nocRepository.save(noc);

            audit(noc, "GENERATED",
                    actor.id(), "Retry successful");

            return NocResponse.from(noc);

        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF retry failed", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<NocResponse> getByLoan(Long loanId, Actor actor) {

        access.requireLoan(actor, loanId, false);
        getLoan(loanId, false);

        return nocRepository
                .findByLoanIdOrderByVersionNumberDesc(loanId)
                .stream()
                .map(NocResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NocResponse getById(Long nocId, Actor actor) {
        Noc noc = requireNoc(nocId);
        access.requireLoan(actor, noc.getLoanId(), false);
        return NocResponse.from(noc);
    }

    @Transactional(readOnly = true)
    public NocResponse verify(String certificateNumber, Actor actor) {

        Noc noc = nocRepository
                .findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Certificate not found"));

        access.requireLoan(actor, noc.getLoanId(), false);
        return NocResponse.from(noc);
    }

    @Transactional
    public byte[] download(
            Long nocId,
            Actor actor) {

        Noc noc = requireNoc(nocId);
        access.requireLoan(actor, noc.getLoanId(), false);

        if (!"AVAILABLE".equals(noc.getNocStatus())
                || noc.getFilePath() == null) {

            throw conflict(
                "Certificate is not available for download");
        }

        try {
            Path directory = storagePath();

            Path file = Paths.get(noc.getFilePath())
                    .toAbsolutePath()
                    .normalize();

            if (!file.startsWith(directory)) {
                throw new SecurityException(
                        "Invalid certificate file location");
            }

            byte[] pdf = Files.readAllBytes(file);

            if (noc.getFileHash() == null
                    || !calculateHash(pdf)
                        .equalsIgnoreCase(noc.getFileHash())) {

                throw new SecurityException(
                        "Certificate integrity verification failed");
            }

            audit(noc, "DOWNLOADED",
                    actor.id(), "Certificate downloaded");

            return pdf;

        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to download certificate", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<NocAudit> history(Long nocId, Actor actor) {

        Noc noc = requireNoc(nocId);
        access.requireLoan(actor, noc.getLoanId(), false);

        return auditRepository
                .findByNocIdOrderByActionAtDesc(nocId);
    }
}
