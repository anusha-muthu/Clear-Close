package com.clearclose.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class LoanClosureServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LoanClosureService service = new LoanClosureService(jdbc);
    private final LoanClosureService.PreClosureRequest request = new LoanClosureService.PreClosureRequest(2L, 24L, 44L);

    private void state(String loanStatus, String requestStatus, boolean verified) throws Exception {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(44L), eq(24L))).thenReturn(List.of(44L));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("BORROWER_ID")).thenReturn(1101L);
        when(rs.getString("LOAN_STATUS")).thenReturn(loanStatus);
        when(rs.getString("REQUEST_STATUS")).thenReturn(requestStatus);
        when(rs.getBigDecimal("OUTSTANDING_AMOUNT")).thenReturn("CLOSED".equals(loanStatus) ? BigDecimal.ZERO : new BigDecimal("100000"));
        when(rs.getBigDecimal("PRINCIPAL_AMOUNT")).thenReturn(new BigDecimal("100000"));
        when(rs.getBigDecimal("AMOUNT")).thenReturn(new BigDecimal("100500"));
        when(rs.getBigDecimal("TOTAL_AMOUNT")).thenReturn(new BigDecimal("100500"));
        when(rs.getInt("VERIFIED")).thenReturn(verified ? 1 : 0);
        when(rs.getTimestamp("CLOSURE_DATE")).thenReturn(Timestamp.valueOf("2026-10-23 12:00:00"));
        doAnswer(call -> {
            RowMapper<?> mapper = call.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), eq(44L), eq(24L), eq(24L), eq(2L));
    }
    @Test void missingWorkflowIdIsRejectedBeforeDatabaseAccess() {
        assertThrows(ResponseStatusException.class, () -> service.closePreClosure(24L, new LoanClosureService.PreClosureRequest(null, 24L, 44L)));
        verifyNoInteractions(jdbc);
    }
    @Test void unverifiedPaymentCannotCloseLoan() throws Exception {
        state("ACTIVE", "PAYMENT_PENDING", false);
        assertThrows(ResponseStatusException.class, () -> service.closePreClosure(24L, request));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void paymentWithoutRecordedOfficerApprovalCannotCloseLoan() throws Exception {
        state("ACTIVE", "PAYMENT_PENDING", true);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(44L))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L), eq(24L), eq(44L))).thenReturn(1);
        assertThrows(ResponseStatusException.class, () -> service.closePreClosure(24L, request));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void retryOfSameCompletedWorkflowReturnsClosureWithoutWritingAgain() throws Exception {
        state("CLOSED", "COMPLETED", true);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(44L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L), eq(24L), eq(44L))).thenReturn(1);
        var result = service.closePreClosure(24L, request);
        assertEquals("CLOSED", result.loanStatus());
        assertEquals(0, result.outstandingAmount().signum());
        assertEquals(44L, result.closureRequestId());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
