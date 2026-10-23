package com.clearclose.exit.preclosure;

import com.clearclose.exit.payment.RazorpayClient;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Nested;
import com.clearclose.exit.preclosure.PreClosureService.PreClosureRecord;

class PreClosureServiceTest {
    @Nested
    class Workflow {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final PreClosureService service = new PreClosureService(jdbc);

        private void owned(String status, Long officerId) {
            when(jdbc.queryForList(anyString(), eq(Long.class), eq(44L), eq(1101L))).thenReturn(List.of(44L));
            var record = new PreClosureRecord(44L, 24L, "FORECLOSURE", status, "Borrower reason", officerId,
                LocalDateTime.of(2026, 10, 23, 12, 0), null, null, null);
            doReturn(List.of(record)).when(jdbc).query(anyString(), any(RowMapper.class), eq(44L), eq(1101L));
        }
        private void officer() {
            when(jdbc.queryForList(anyString(), eq(Long.class), eq(44L), eq(2101L), eq(2101L))).thenReturn(List.of(1101L));
        }
        @Test void quoteCannotBeGeneratedBeforeOfficerApproval() {
            owned("PENDING", null);
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.createQuote(1101L, 44L)).getStatusCode());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        }
        @Test void borrowerCannotAcceptBeforeApproval() {
            owned("UNDER_REVIEW", 2101L);
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.accept(1101L, 44L)).getStatusCode());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        }
        @Test void approvalRecordsDecisionAndDoesNotCloseLoan() {
            officer(); owned("UNDER_REVIEW", 2101L);
            when(jdbc.update(contains("SET STATUS = 'APPROVED'"), eq(2101L), eq(44L))).thenReturn(1);
            service.approve(2101L, 44L);
            verify(jdbc).update(contains("SET STATUS = 'APPROVED'"), eq(2101L), eq(44L));
            verify(jdbc, never()).update(contains("UPDATE LOAN_ACCOUNT"), any(Object[].class));
            verify(jdbc).update(contains("INSERT INTO AUDIT_LOG"), eq(2101L), eq(44L), eq("PRE_CLOSURE_APPROVED"), isNull(), eq(44L));
        }
        @Test void repeatedApprovalDoesNotDuplicateApprovalAudit() {
            officer(); owned("APPROVED", 2101L);
            service.approve(2101L, 44L);
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        }
        @Test void cannotApproveAfterPaymentHasStarted() {
            officer(); owned("PAYMENT_PENDING", 2101L);
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.approve(2101L, 44L)).getStatusCode());
            verify(jdbc, never()).update(contains("INSERT INTO AUDIT_LOG"), any(Object[].class));
        }
        @Test void anotherOfficersAssignedRequestIsDenied() {
            when(jdbc.queryForList(anyString(), eq(Long.class), eq(44L), eq(2102L), eq(2102L))).thenReturn(List.of());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.approve(2102L, 44L)).getStatusCode());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        }
        @Test void reasonValidationRespectsOracleByteLimit() {
            assertDoesNotThrow(() -> PreClosureService.validateReason("a".repeat(1000), true));
            assertThrows(ResponseStatusException.class, () -> PreClosureService.validateReason("₹".repeat(334), true));
            assertThrows(ResponseStatusException.class, () -> PreClosureService.validateReason(" ", true));
        }
    }

    @Nested
    class IdentityChecks {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final PreClosureService access = new PreClosureService(jdbc);
        private Jwt token(String type, String role, Object id, String subject) {
            return Jwt.withTokenValue("test").header("alg", "HS256").subject(subject)
                .claim("accountType", type).claim("role", role).claim("userId", id).build();
        }
        @Test void readsNumericUserIdInsteadOfParsingSubject() {
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1101L))).thenReturn(1);
            assertEquals(1101L, access.borrower(token("BORROWER", "BORROWER", 1101L, "BORROWER:1101")));
        }
        @Test void acceptsClosureOfficerWithActiveDatabaseAccount() {
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2101L))).thenReturn(1);
            assertEquals(2101L, access.officer(token("OFFICER", "LOAN_CLOSURE_OFFICER", 2101L, "OFFICER:2101")));
        }
        @Test void missingAuthenticationIs401() {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> access.borrower(null)).getStatusCode());
            verifyNoInteractions(jdbc);
        }
        @Test void borrowerCannotActAsOfficer() {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> access.officer(token("BORROWER", "BORROWER", 1101L, "BORROWER:1101"))).getStatusCode());
            verifyNoInteractions(jdbc);
        }
        @Test void rejectsInconsistentIdentityClaims() {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> access.borrower(token("BORROWER", "BORROWER", 1101L, "BORROWER:999"))).getStatusCode());
            verifyNoInteractions(jdbc);
        }
        @Test void rejectsFractionalUserId() {
            assertThrows(ResponseStatusException.class, () -> access.borrower(token("BORROWER", "BORROWER", 1101.5, "BORROWER:1101")));
            verifyNoInteractions(jdbc);
        }
        @Test void disabledDatabaseAccountCannotUseAnOtherwiseValidToken() {
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1101L))).thenReturn(0);
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> access.borrower(token("BORROWER", "BORROWER", 1101L, "BORROWER:1101"))).getStatusCode());
        }
    }

    @Nested
    class Payments {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final RazorpayClient razorpay = mock(RazorpayClient.class);
        private final PreClosureService workflow = mock(PreClosureService.class);
        private final PreClosurePaymentService payments = new PreClosurePaymentService(jdbc, razorpay, workflow);

        private void quote(boolean approved, boolean valid) throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("QUOTE_ID")).thenReturn(24L);
            when(rs.getLong("CLOSURE_REQUEST_ID")).thenReturn(44L);
            when(rs.getBigDecimal("TOTAL_AMOUNT")).thenReturn(new BigDecimal("100500.00"));
            when(rs.getString("QUOTE_STATUS")).thenReturn("ACCEPTED");
            when(rs.getString("REQUEST_STATUS")).thenReturn("PAYMENT_PENDING");
            when(rs.getString("LOAN_STATUS")).thenReturn("ACTIVE");
            when(rs.getInt("VALID_QUOTE")).thenReturn(valid ? 1 : 0);
            when(rs.getInt("APPROVED")).thenReturn(approved ? 1 : 0);
            doAnswer(call -> {
                RowMapper<?> mapper = call.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            }).when(jdbc).query(anyString(), any(RowMapper.class), eq(44L), eq(1101L));
        }
        private Map<String, Object> initiated() {
            return Map.of("PAYMENT_ID", 2L, "RAZORPAY_ORDER_ID", "order_demo", "PAYMENT_STATUS", "INITIATED",
                "AMOUNT", new BigDecimal("100500.00"));
        }
        @Test void orderRequiresRecordedApproval() throws Exception {
            quote(false, true);
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> payments.createOrder(1101L, 44L)).getStatusCode());
            verifyNoInteractions(razorpay);
        }
        @Test void expiredAcceptedQuoteCannotOpenCheckout() throws Exception {
            quote(true, false);
            assertThrows(ResponseStatusException.class, () -> payments.createOrder(1101L, 44L));
            verifyNoInteractions(razorpay);
        }
        @Test void invalidSignatureNeverConfirmsOrQueriesProvider() throws Exception {
            quote(true, true);
            when(jdbc.queryForMap(anyString(), eq(24L), eq("order_demo"))).thenReturn(initiated());
            when(razorpay.keySecret()).thenReturn("unit-test-secret");
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> payments.verify(1101L, 44L, new PreClosurePaymentService.VerifyRequest("order_demo", "pay_demo", "0".repeat(64)))).getStatusCode());
            verify(razorpay, never()).payment(anyString());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        }
        @Test void providerAmountMismatchDoesNotMarkPaymentSuccessful() throws Exception {
            quote(true, false);
            when(jdbc.queryForList(anyString(), eq(24L))).thenReturn(List.of(initiated()));
            when(razorpay.orderPayments("order_demo")).thenReturn(List.of(Map.of("id", "pay_demo", "order_id", "order_demo",
                "status", "captured", "currency", "INR", "amount", 1L)));
            assertThrows(ResponseStatusException.class, () -> payments.status(1101L, 44L));
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        }
        @Test void refreshRecoversCapturedPaymentAfterLostBrowserCallback() throws Exception {
            // Existing orders can be reconciled after expiry; expiry blocks opening new checkout.
            quote(true, false);
            when(jdbc.queryForList(anyString(), eq(24L))).thenReturn(List.of(initiated()));
            when(razorpay.orderPayments("order_demo")).thenReturn(List.of(Map.of("id", "pay_demo", "order_id", "order_demo",
                "status", "captured", "currency", "INR", "amount", 10050000L, "method", "upi")));
            when(jdbc.update(contains("UPDATE PAYMENT"), eq("pay_demo"), eq("UPI"), eq(2L))).thenReturn(1);
            when(jdbc.update(contains("UPDATE SETTLEMENT_QUOTE"), eq(24L))).thenReturn(1);
            when(jdbc.queryForMap(anyString(), eq(2L))).thenReturn(Map.of("PAYMENT_ID", 2L,
                "RAZORPAY_ORDER_ID", "order_demo", "RAZORPAY_PAYMENT_ID", "pay_demo", "PAYMENT_STATUS", "SUCCESS"));
            assertEquals("SUCCESS", payments.status(1101L, 44L).status());
            verify(workflow).lockOwned(1101L, 44L);
            verify(jdbc).update(contains("PRE_CLOSURE_PAYMENT_VERIFIED"), eq(1101L), eq(2L), eq(2L), eq(24L), eq(44L));
        }
    }
}
