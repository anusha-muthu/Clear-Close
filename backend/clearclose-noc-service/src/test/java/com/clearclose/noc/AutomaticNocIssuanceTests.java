package com.clearclose.noc;

import com.clearclose.noc.entity.Noc;
import com.clearclose.noc.repository.NocAuditRepository;
import com.clearclose.noc.repository.NocRepository;
import com.clearclose.noc.security.NocAccess;
import com.clearclose.noc.service.AutomaticNocIssuance;
import com.clearclose.noc.service.NocService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutomaticNocIssuanceTests {
    @TempDir Path storage;
    JdbcTemplate jdbc;
    NocRepository nocs;
    NocAuditRepository audits;
    NocAccess access;
    NocService service;
    ResultSet loan;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        nocs = mock(NocRepository.class);
        audits = mock(NocAuditRepository.class);
        access = mock(NocAccess.class);
        service = new NocService(nocs, audits, jdbc, access);
        ReflectionTestUtils.setField(service, "storageDirectory", storage.toString());
        ReflectionTestUtils.setField(service, "lenderName", "Test Bank");
        loan = mock(ResultSet.class);
        when(loan.getLong("LOAN_ID")).thenReturn(41L);
        when(loan.getLong("BORROWER_ID")).thenReturn(21L);
        when(loan.getString("BORROWER_NAME")).thenReturn("Test Borrower");
        when(loan.getString("LOAN_REFERENCE")).thenReturn("LOAN-41");
        when(loan.getString("LOAN_STATUS")).thenReturn("CLOSED");
        when(loan.getBigDecimal("OUTSTANDING_AMOUNT")).thenReturn(BigDecimal.ZERO);
        when(loan.getDate("CLOSURE_DATE")).thenReturn(Date.valueOf("2026-10-24"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(41L))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(loan, 0));
        });
        when(nocs.findTopByLoanIdOrderByVersionNumberDesc(41L)).thenReturn(Optional.empty());
        when(nocs.saveAndFlush(any(Noc.class))).thenAnswer(invocation -> {
            Noc noc = invocation.getArgument(0);
            noc.setNocId(1L);
            return noc;
        });
    }

    @Test
    void closedLoanProducesDownloadablePdfWithSystemActor() throws Exception {
        var result = service.generateAutomatically(41L);
        assertEquals("AVAILABLE", result.status());
        assertEquals(1, result.versionNumber());
        Path pdf = storage.resolve(result.certificateNumber() + ".pdf");
        assertTrue(Files.size(pdf) > 0);
        assertTrue(new String(Files.readAllBytes(pdf), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        verify(audits).save(argThat(audit -> "GENERATED".equals(audit.getAction()) && audit.getActorId() == null));
        verifyNoInteractions(access);
    }

    @Test
    void repeatedClosureDoesNotCreateAnotherCertificate() {
        Noc existing = new Noc();
        existing.setNocId(5L);
        existing.setNocStatus("AVAILABLE");
        when(nocs.findTopByLoanIdOrderByVersionNumberDesc(41L)).thenReturn(Optional.of(existing));
        assertEquals(5L, service.generateAutomatically(41L).nocId());
        verify(nocs, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test
    void outstandingDuesPreventIssuance() throws Exception {
        when(loan.getBigDecimal("OUTSTANDING_AMOUNT")).thenReturn(BigDecimal.ONE);
        assertThrows(ResponseStatusException.class, () -> service.generateAutomatically(41L));
        verify(nocs, never()).saveAndFlush(any());
    }

    @Test
    void activeLoanPreventsIssuance() throws Exception {
        when(loan.getString("LOAN_STATUS")).thenReturn("ACTIVE");
        assertThrows(ResponseStatusException.class, () -> service.generateAutomatically(41L));
        verify(nocs, never()).saveAndFlush(any());
    }

    @Test
    void missingClosureDatePreventsIssuance() throws Exception {
        when(loan.getDate("CLOSURE_DATE")).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.generateAutomatically(41L));
        verify(nocs, never()).saveAndFlush(any());
    }

    @Test
    void failedPdfIsRecordedForOfficerRetry() throws Exception {
        Path file = storage.resolve("file");
        Files.writeString(file, "not a directory");
        ReflectionTestUtils.setField(service, "storageDirectory", file.toString());
        assertEquals("FAILED", service.generateAutomatically(41L).status());
        verify(audits).save(argThat(audit -> "FAILED".equals(audit.getAction())));
    }

    @Test
    void oneFailureDoesNotBlockRemainingLoans() {
        NocService worker = mock(NocService.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(41L, 42L));
        when(worker.generateAutomatically(41L)).thenThrow(new IllegalStateException("unavailable"));
        when(worker.generateAutomatically(42L)).thenReturn(new com.clearclose.noc.dto.NocResponse(
                1L, 42L, "NOC-42", "AVAILABLE", 1, null, null, null));
        new AutomaticNocIssuance(jdbc, worker).issueClosedLoans();
        verify(worker).generateAutomatically(42L);
    }

    @Test
    void certificateListingRequiresAuthorizedOfficer() {
        var borrower = new NocAccess.Actor(21L, "BORROWER", "BORROWER");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(access).requireOfficerRead(borrower);
        assertThrows(ResponseStatusException.class, () -> service.recentCertificates(borrower));
        verify(nocs, never()).findTop100ByOrderByCreatedAtDescNocIdDesc();
    }

    private Noc certificate(long id, int version, String status) {
        Noc noc = new Noc();
        noc.setNocId(id);
        noc.setLoanId(41L);
        noc.setVersionNumber(version);
        noc.setCertificateNumber("NOC-TEST-" + id);
        noc.setIssueDate(java.time.LocalDate.of(2026, 10, 24));
        noc.setNocStatus(status);
        return noc;
    }

    @Test
    void successfulReissueRevokesOriginalAndRecordsOfficer() {
        Noc original = certificate(5L, 1, "AVAILABLE");
        when(nocs.findTopByLoanIdOrderByVersionNumberDesc(41L)).thenReturn(Optional.of(original));
        var officer = new NocAccess.Actor(2101L, "OFFICER", "LOAN_CLOSURE_OFFICER");

        var result = service.reissue(41L, officer);

        assertEquals("AVAILABLE", result.status());
        assertEquals(2, result.versionNumber());
        assertNotEquals(original.getCertificateNumber(), result.certificateNumber());
        assertEquals("REVOKED", original.getNocStatus());
        verify(access).requireLoan(officer, 41L, true);
        verify(audits).save(argThat(a -> "REISSUED".equals(a.getAction()) && Long.valueOf(2101L).equals(a.getActorId())));
    }

    @Test
    void failedReissueKeepsOriginalAvailable() throws Exception {
        Noc original = certificate(5L, 1, "AVAILABLE");
        when(nocs.findTopByLoanIdOrderByVersionNumberDesc(41L)).thenReturn(Optional.of(original));
        Path file = storage.resolve("blocked");
        Files.writeString(file, "not a directory");
        ReflectionTestUtils.setField(service, "storageDirectory", file.toString());

        var result = service.reissue(41L, new NocAccess.Actor(2101L, "OFFICER", "LOAN_CLOSURE_OFFICER"));

        assertEquals("FAILED", result.status());
        assertEquals("AVAILABLE", original.getNocStatus());
        verify(audits, never()).save(argThat(a -> "REVOKED".equals(a.getAction())));
    }

    @Test
    void recoveredReplacementRevokesOriginal() {
        Noc original = certificate(5L, 1, "AVAILABLE");
        Noc replacement = certificate(6L, 2, "FAILED");
        when(nocs.findById(6L)).thenReturn(Optional.of(replacement));
        when(nocs.findTopByLoanIdOrderByVersionNumberDesc(41L)).thenReturn(Optional.of(replacement));
        when(nocs.findByLoanIdOrderByVersionNumberDesc(41L)).thenReturn(List.of(replacement, original));
        when(nocs.save(any(Noc.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.retry(6L, new NocAccess.Actor(2101L, "OFFICER", "LOAN_CLOSURE_OFFICER"));

        assertEquals("AVAILABLE", result.status());
        assertEquals("REVOKED", original.getNocStatus());
        assertEquals("AVAILABLE", replacement.getNocStatus());
        verify(audits).save(argThat(a -> "REVOKED".equals(a.getAction())));
    }
}
