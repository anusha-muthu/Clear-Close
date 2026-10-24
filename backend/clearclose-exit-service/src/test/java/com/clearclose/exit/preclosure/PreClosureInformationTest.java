package com.clearclose.exit.preclosure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import com.clearclose.exit.preclosure.PreClosureService.LoanSummary;
import com.clearclose.exit.preclosure.PreClosureService.PreClosureRecord;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreClosureInformationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PreClosureService service = spy(new PreClosureService(jdbc));

    private PreClosureRecord record(String status) {
        var loan = new LoanSummary(1101L, "Tara", "TEST-24", "PERSONAL_LOAN",
            new BigDecimal("50000"), new BigDecimal("25000"), new BigDecimal("12"), "ACTIVE", null, null);
        return new PreClosureRecord(44L, 24L, "FORECLOSURE", status, "Original reason", 2101L,
            LocalDateTime.of(2026, 10, 24, 12, 0), null, loan, null);
    }

    private void officerOwnsRequest() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(44L), eq(2101L), eq(2101L)))
            .thenReturn(List.of(1101L));
    }

    private void borrowerOwnsRequest() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(44L), eq(1101L))).thenReturn(List.of(44L));
    }

    @Test void officerQuestionUsesExistingAuditAndLeavesDatabaseStatusUnderReview() {
        officerOwnsRequest();
        doReturn(record("UNDER_REVIEW"), record("MORE_INFO_REQUIRED")).when(service).get(1101L, 44L);
        when(jdbc.update(contains("SET UPDATED_AT"), eq(44L), eq(2101L))).thenReturn(1);
        assertEquals("MORE_INFO_REQUIRED", service.requestInformation(2101L, 44L, "Explain the repayment source").status());
        verify(jdbc).update(contains("INSERT INTO AUDIT_LOG"), eq(2101L), eq(44L),
            eq("PRE_CLOSURE_MORE_INFO_REQUIRED"), eq("Explain the repayment source"), eq(44L));
        verify(jdbc, never()).update(contains("SET STATUS = 'MORE_INFO_REQUIRED'"), any(Object[].class));
        verify(jdbc, never()).update(contains("UPDATE LOAN_ACCOUNT"), any(Object[].class));
    }

    @Test void borrowerReplyReturnsSameRequestForReview() {
        borrowerOwnsRequest();
        doReturn(record("MORE_INFO_REQUIRED"), record("UNDER_REVIEW")).when(service).get(1101L, 44L);
        when(jdbc.update(contains("SET UPDATED_AT"), eq(44L))).thenReturn(1);
        var updated = service.submitInformation(1101L, 44L, "Repayment is from my savings");
        assertEquals("UNDER_REVIEW", updated.status());
        assertEquals(44L, updated.closureRequestId());
        assertEquals("Original reason", updated.reason());
        verify(jdbc).update(contains("INSERT INTO AUDIT_LOG"), eq(1101L), eq(44L),
            eq("PRE_CLOSURE_INFO_SUBMITTED"), eq("Repayment is from my savings"), eq(44L));
    }

    @Test void officerCannotApproveWhileAReplyIsOutstanding() {
        officerOwnsRequest();
        doReturn(record("MORE_INFO_REQUIRED")).when(service).get(1101L, 44L);
        assertThrows(ResponseStatusException.class, () -> service.approve(2101L, 44L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void repeatedReplyIsBlockedAfterRequestReturnsToReview() {
        borrowerOwnsRequest();
        doReturn(record("UNDER_REVIEW")).when(service).get(1101L, 44L);
        assertThrows(ResponseStatusException.class, () -> service.submitInformation(1101L, 44L, "Duplicate reply"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void anotherBorrowerCannotReply() {
        var error = assertThrows(ResponseStatusException.class,
            () -> service.submitInformation(999L, 44L, "Unauthorized reply"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void anotherOfficerCannotRequestInformation() {
        var error = assertThrows(ResponseStatusException.class,
            () -> service.requestInformation(999L, 44L, "Unauthorized question"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void acceptedSettlementBlocksInformationExchange() {
        officerOwnsRequest();
        doReturn(record("UNDER_REVIEW")).when(service).get(1101L, 44L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(44L))).thenReturn(1);
        assertThrows(ResponseStatusException.class, () -> service.requestInformation(2101L, 44L, "New question"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void blankAndOversizedMessagesAreRejectedBeforeDatabaseAccess() {
        assertThrows(ResponseStatusException.class, () -> service.requestInformation(2101L, 44L, " "));
        assertThrows(ResponseStatusException.class, () -> service.submitInformation(1101L, 44L, "\u20b9".repeat(334)));
        verifyNoInteractions(jdbc);
    }

    @Test @SuppressWarnings("unchecked")
    void storedUnderReviewIsDisplayedAsWaitingOnlyWhenLatestConversationEntryIsAQuestion() throws Exception {
        var realService = new PreClosureService(jdbc);
        var rs = mock(ResultSet.class);
        when(rs.getLong("CLOSURE_REQUEST_ID")).thenReturn(44L);
        when(rs.getString("STATUS")).thenReturn("UNDER_REVIEW");
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.valueOf("2026-10-24 12:00:00"));
        when(jdbc.query(contains("SELECT c.*"), any(RowMapper.class), eq(44L), eq(1101L)))
            .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(rs, 0)));
        when(jdbc.queryForList(contains("SELECT ACTION"), eq(String.class), eq(44L), eq(44L)))
            .thenReturn(List.of("PRE_CLOSURE_MORE_INFO_REQUIRED"), List.of("PRE_CLOSURE_INFO_SUBMITTED"));
        assertEquals("MORE_INFO_REQUIRED", realService.get(1101L, 44L).status());
        assertEquals("UNDER_REVIEW", realService.get(1101L, 44L).status());
    }
}
