package com.clearclose.exit.preclosure;

import java.time.LocalDateTime;
import java.time.LocalDate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PreClosureService {
    private final JdbcTemplate jdbc;
    public PreClosureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private long insert(String sql, String column, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{column});
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("Generated ID is missing");
        return id.longValue();
    }

    static void validateReason(String reason, boolean required) {
        if ((required && (reason == null || reason.isBlank())) || (reason != null
                && reason.getBytes(StandardCharsets.UTF_8).length > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason must contain at most 1000 UTF-8 bytes");
        }
    }

    private void audit(long actorId, long requestId, boolean officer, String action, String details) {
        jdbc.update("INSERT INTO AUDIT_LOG (" + (officer ? "OFFICER_ID" : "USER_ID") + """
            , ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS, CLOSURE_REQUEST_ID)
            VALUES (?, ?, 'LOAN_CLOSURE_REQUEST', ?, ?, ?)
            """, actorId, requestId, action, details, requestId);
    }

    // Every mutation on an existing request takes this lock before quote/payment locks.
    public void lockOwned(long borrowerId, long requestId) {
        var rows = jdbc.queryForList("""
            SELECT c.CLOSURE_REQUEST_ID FROM LOAN_CLOSURE_REQUEST c
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            WHERE c.CLOSURE_REQUEST_ID = ? AND l.BORROWER_ID = ?
              AND c.CLOSURE_TYPE = 'FORECLOSURE' FOR UPDATE OF c.STATUS
            """, Long.class, requestId, borrowerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pre-closure request not found");
    }

    @Transactional
    public PreClosureRecord create(long borrowerId, PreClosureRequest request) {
        if (request == null || request.loanId() == null || request.loanId() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid loan ID is required");
        validateReason(request.reason(), false);
        var loans = jdbc.queryForList("""
            SELECT LOAN_ID FROM LOAN_ACCOUNT WHERE LOAN_ID = ? AND BORROWER_ID = ?
              AND LOAN_STATUS = 'ACTIVE' AND OUTSTANDING_AMOUNT > 0 FOR UPDATE
            """, Long.class, request.loanId(), borrowerId);
        if (loans.isEmpty()) throw conflict("An owned active loan with outstanding principal is required");
        Integer allowed = jdbc.queryForObject("""
            SELECT COUNT(*) FROM LOAN_ACCOUNT l JOIN LOAN_PRODUCT_RULE r ON r.RULE_ID = l.RULE_ID
            WHERE l.LOAN_ID = ? AND r.EARLY_CLOSURE_ALLOWED = 'Y' AND r.RULE_STATUS = 'ACTIVE'
              AND l.DISBURSEMENT_DATE <= SYSDATE AND l.MATURITY_DATE > SYSDATE
            """, Integer.class, request.loanId());
        if (allowed == null || allowed != 1) throw conflict("Loan is not eligible for early closure");
        Integer competing = jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM COOLING_OFF_REQUEST WHERE LOAN_ID = ?
                     AND STATUS IN ('PENDING','UNDER_REVIEW','MORE_INFO_REQUIRED','APPROVED'))
                 + (SELECT COUNT(*) FROM LOAN_CLOSURE_REQUEST WHERE LOAN_ID = ?
                     AND STATUS IN ('PENDING','UNDER_REVIEW','PAYMENT_PENDING','APPROVED')) FROM DUAL
            """, Integer.class, request.loanId(), request.loanId());
        if (competing != null && competing > 0) throw conflict("Another loan exit or closure request is active");
        long id = insert("""
            INSERT INTO LOAN_CLOSURE_REQUEST (LOAN_ID, CLOSURE_TYPE, STATUS, REASON)
            VALUES (?, 'FORECLOSURE', 'PENDING', ?)
            """, "CLOSURE_REQUEST_ID", request.loanId(), request.reason());
        audit(borrowerId, id, false, "PRE_CLOSURE_CREATED", null);
        return get(borrowerId, id);
    }

    public PreClosureRecord get(long borrowerId, long requestId) {
        var rows = jdbc.query("""
            SELECT c.*, l.BORROWER_ID, b.NAME AS BORROWER_NAME, l.LOAN_REFERENCE,
                r.PRODUCT_TYPE, l.PRINCIPAL_AMOUNT, l.OUTSTANDING_AMOUNT, l.INTEREST_RATE,
                l.LOAN_STATUS, l.MATURITY_DATE, l.CLOSURE_DATE
            FROM LOAN_CLOSURE_REQUEST c JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            JOIN BORROWERS b ON b.USER_ID = l.BORROWER_ID JOIN LOAN_PRODUCT_RULE r ON r.RULE_ID = l.RULE_ID
            WHERE c.CLOSURE_REQUEST_ID = ? AND c.CLOSURE_TYPE = 'FORECLOSURE' AND l.BORROWER_ID = ?
            """, (rs, n) -> {
                Number officer = (Number) rs.getObject("OFFICER_ID");
                Timestamp created = rs.getTimestamp("CREATED_AT");
                Timestamp maturity = rs.getTimestamp("MATURITY_DATE");
                Timestamp closure = rs.getTimestamp("CLOSURE_DATE");
                return new PreClosureRecord(rs.getLong("CLOSURE_REQUEST_ID"), rs.getLong("LOAN_ID"),
                    rs.getString("CLOSURE_TYPE"), rs.getString("STATUS"), rs.getString("REASON"),
                    officer == null ? null : officer.longValue(), created.toLocalDateTime(), quoteForRequest(requestId),
                    new LoanSummary(rs.getLong("BORROWER_ID"), rs.getString("BORROWER_NAME"), rs.getString("LOAN_REFERENCE"),
                        rs.getString("PRODUCT_TYPE"), rs.getBigDecimal("PRINCIPAL_AMOUNT"), rs.getBigDecimal("OUTSTANDING_AMOUNT"),
                        rs.getBigDecimal("INTEREST_RATE"), rs.getString("LOAN_STATUS"),
                        maturity == null ? null : maturity.toLocalDateTime(), closure == null ? null : closure.toLocalDateTime()),
                    "REJECTED".equals(rs.getString("STATUS")) ? rejectionReason(requestId) : null);
            }, requestId, borrowerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pre-closure request not found");
        return rows.getFirst();
    }

    private String rejectionReason(long id) {
        var reasons = jdbc.queryForList("""
            SELECT DETAILS FROM AUDIT_LOG WHERE CLOSURE_REQUEST_ID = ? AND ACTION = 'PRE_CLOSURE_REJECTED'
            ORDER BY LOG_ID DESC FETCH FIRST 1 ROW ONLY
            """, String.class, id);
        return reasons.isEmpty() ? null : reasons.getFirst();
    }

    public List<PreClosureRecord> myRequests(long borrowerId) {
        var ids = jdbc.queryForList("""
            SELECT c.CLOSURE_REQUEST_ID FROM LOAN_CLOSURE_REQUEST c JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            WHERE l.BORROWER_ID = ? AND c.CLOSURE_TYPE = 'FORECLOSURE'
            ORDER BY c.CREATED_AT DESC, c.CLOSURE_REQUEST_ID DESC
            """, Long.class, borrowerId);
        return ids.stream().map(id -> get(borrowerId, id)).toList();
    }

    private PreClosureQuote quoteForRequest(long id) {
        var quotes = jdbc.query("""
            SELECT q.*, CASE WHEN VALID_UNTIL <= SYSDATE THEN 1 ELSE 0 END AS EXPIRED
            FROM SETTLEMENT_QUOTE q WHERE CLOSURE_REQUEST_ID = ? AND QUOTE_TYPE = 'CLOSURE'
            ORDER BY QUOTE_ID DESC
            """, (rs, n) -> new PreClosureQuote(rs.getLong("QUOTE_ID"), rs.getLong("CLOSURE_REQUEST_ID"),
                rs.getLong("LOAN_ID"), rs.getBigDecimal("PRINCIPAL_AMOUNT"), rs.getBigDecimal("INTEREST_AMOUNT"),
                rs.getBigDecimal("CHARGES_AMOUNT"), rs.getBigDecimal("TAX_AMOUNT"), rs.getBigDecimal("TOTAL_AMOUNT"),
                rs.getString("QUOTE_STATUS"), rs.getTimestamp("VALID_UNTIL").toLocalDateTime(), rs.getInt("EXPIRED") == 1,
                rs.getObject("CALCULATION_VERSION") == null ? null : new CalculationBasis(
                    rs.getDate("INTEREST_PAID_THROUGH").toLocalDate(), rs.getDate("INTEREST_AS_OF_DATE").toLocalDate(),
                    rs.getLong("ACCRUED_INTEREST_DAYS"), rs.getBigDecimal("QUOTED_ANNUAL_RATE"), "Actual/365",
                    rs.getString("APPLIED_CHARGE_TYPE"), rs.getBigDecimal("APPLIED_CHARGE_VALUE"),
                    rs.getBigDecimal("APPLIED_TAX_RATE"), rs.getString("CHARGE_POLICY_REASON")),
                rs.getObject("CALCULATION_VERSION") != null
                    && ((Number) rs.getObject("CALCULATION_VERSION")).intValue() == 2), id);
        return quotes.isEmpty() ? null : quotes.getFirst();
    }

    @Transactional
    public PreClosureQuote createQuote(long borrowerId, long id) {
        lockOwned(borrowerId, id);
        var record = get(borrowerId, id);
        if (!"APPROVED".equals(record.status()) && !"PAYMENT_PENDING".equals(record.status()))
            throw conflict("Officer approval is required before quotation");
        var previous = record.quote();
        if (previous != null && ("ACCEPTED".equals(previous.quoteStatus()) || "PAID".equals(previous.quoteStatus())
                || ("AVAILABLE".equals(previous.quoteStatus()) && !previous.expired() && !previous.legacyFeePolicy()))) return previous;
        if (!"APPROVED".equals(record.status())) throw conflict("Accepted quotes cannot be replaced");
        var loans = jdbc.query("""
            SELECT l.OUTSTANDING_AMOUNT, l.INTEREST_RATE, l.DISBURSEMENT_DATE,
                   l.INTEREST_PAID_THROUGH, l.INTEREST_RATE_TYPE, l.LOAN_PURPOSE,
                   l.PREPAYMENT_CHARGE_ALLOWED, TRUNC(SYSDATE) AS SETTLEMENT_DATE,
                   r.CLOSURE_CHARGE_TYPE, r.CLOSURE_CHARGE_VALUE, r.CLOSURE_TAX_RATE
            FROM LOAN_ACCOUNT l JOIN LOAN_PRODUCT_RULE r ON r.RULE_ID = l.RULE_ID
            WHERE l.LOAN_ID = ? AND l.BORROWER_ID = ? AND l.LOAN_STATUS = 'ACTIVE'
              AND r.EARLY_CLOSURE_ALLOWED = 'Y' AND r.RULE_STATUS = 'ACTIVE'
              AND l.DISBURSEMENT_DATE <= SYSDATE AND l.MATURITY_DATE > SYSDATE
            FOR UPDATE OF l.OUTSTANDING_AMOUNT, r.CLOSURE_CHARGE_VALUE
            """, (rs, n) -> new PreClosureSettlementCalculator.Terms(
                rs.getBigDecimal("OUTSTANDING_AMOUNT"), rs.getBigDecimal("INTEREST_RATE"),
                rs.getDate("DISBURSEMENT_DATE").toLocalDate(),
                rs.getDate("INTEREST_PAID_THROUGH") == null ? null : rs.getDate("INTEREST_PAID_THROUGH").toLocalDate(),
                rs.getDate("SETTLEMENT_DATE").toLocalDate(), rs.getString("INTEREST_RATE_TYPE"),
                rs.getString("LOAN_PURPOSE"), rs.getString("PREPAYMENT_CHARGE_ALLOWED"),
                rs.getString("CLOSURE_CHARGE_TYPE"), rs.getBigDecimal("CLOSURE_CHARGE_VALUE"),
                rs.getBigDecimal("CLOSURE_TAX_RATE")), record.loanId(), borrowerId);
        if (loans.size() != 1) throw conflict("Loan is no longer eligible for quotation");
        var terms = loans.getFirst();
        PreClosureSettlementCalculator.Calculation calculation;
        try {
            calculation = PreClosureSettlementCalculator.calculate(terms);
        } catch (IllegalArgumentException ex) {
            throw conflict(ex.getMessage());
        }
        insert("""
            INSERT INTO SETTLEMENT_QUOTE (LOAN_ID, CLOSURE_REQUEST_ID, QUOTE_TYPE, PRINCIPAL_AMOUNT,
                INTEREST_AMOUNT, CHARGES_AMOUNT, TAX_AMOUNT, TOTAL_AMOUNT, VALID_UNTIL, QUOTE_STATUS,
                CALCULATION_VERSION, INTEREST_PAID_THROUGH, INTEREST_AS_OF_DATE, ACCRUED_INTEREST_DAYS,
                QUOTED_ANNUAL_RATE, APPLIED_CHARGE_TYPE, APPLIED_CHARGE_VALUE, APPLIED_TAX_RATE, CHARGE_POLICY_REASON)
            VALUES (?, ?, 'CLOSURE', ?, ?, ?, ?, ?, LEAST(SYSDATE + 15/1440, TRUNC(SYSDATE) + 1), 'AVAILABLE',
                1, ?, ?, ?, ?, ?, ?, ?, ?)
            """, "QUOTE_ID", record.loanId(), id, calculation.principal(), calculation.interest(), calculation.charges(),
            calculation.tax(), calculation.total(), java.sql.Date.valueOf(terms.interestPaidThrough()),
            java.sql.Date.valueOf(terms.settlementDate()), calculation.interestDays(), terms.annualInterestRate(),
            calculation.appliedChargeType(), calculation.appliedChargeValue(), calculation.appliedTaxRate(), calculation.chargePolicyReason());
        audit(borrowerId, id, false, "PRE_CLOSURE_QUOTE_CREATED", null);
        return quoteForRequest(id);
    }

    @Transactional
    public PreClosureRecord accept(long borrowerId, long id) {
        lockOwned(borrowerId, id);
        var record = get(borrowerId, id);
        if ("PAYMENT_PENDING".equals(record.status()) && record.quote() != null
                && "ACCEPTED".equals(record.quote().quoteStatus()) && !record.quote().expired()) return record;
        if (!"APPROVED".equals(record.status()) || record.officerId() == null || record.quote() == null)
            throw conflict("An approved request and quotation are required");
        if (record.quote().legacyFeePolicy())
            throw conflict("Generate a fresh quotation under the current pre-closure fee policy before accepting");
        int changed = jdbc.update("""
            UPDATE SETTLEMENT_QUOTE SET QUOTE_STATUS = 'ACCEPTED', ACCEPTED_AT = SYSTIMESTAMP,
                UPDATED_AT = SYSTIMESTAMP WHERE QUOTE_ID = ? AND QUOTE_STATUS = 'AVAILABLE' AND VALID_UNTIL > SYSDATE
            """, record.quote().quoteId());
        if (changed != 1) throw conflict("Quotation is expired or already processed");
        jdbc.update("UPDATE LOAN_CLOSURE_REQUEST SET STATUS = 'PAYMENT_PENDING', UPDATED_AT = SYSTIMESTAMP WHERE CLOSURE_REQUEST_ID = ?", id);
        audit(borrowerId, id, false, "PRE_CLOSURE_QUOTE_ACCEPTED", null);
        return get(borrowerId, id);
    }

    private long officerRequest(long officerId, long id) {
        var rows = jdbc.queryForList("""
            SELECT l.BORROWER_ID FROM LOAN_CLOSURE_REQUEST c JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            WHERE c.CLOSURE_REQUEST_ID = ? AND c.CLOSURE_TYPE = 'FORECLOSURE'
              AND (c.OFFICER_ID IS NULL OR c.OFFICER_ID = ?)
              AND EXISTS (SELECT 1 FROM OFFICER o WHERE o.OFFICER_ID = ?
                  AND o.ROLE = 'LOAN_CLOSURE_OFFICER' AND o.ACCOUNT_STATUS = 'ACTIVE'
                  AND (o.USER_ID IS NULL OR o.USER_ID <> l.BORROWER_ID))
            FOR UPDATE OF c.STATUS
            """, Long.class, id, officerId, officerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request is unavailable to this officer");
        return rows.getFirst();
    }

    @Transactional
    public PreClosureRecord officerDetails(long officerId, long id) {
        return get(officerRequest(officerId, id), id);
    }

    @Transactional
    public void assign(long officerId, long id) {
        officerRequest(officerId, id);
        int changed = jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST SET OFFICER_ID = ?, STATUS = 'UNDER_REVIEW', UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ? AND STATUS IN ('PENDING','UNDER_REVIEW')
            """, officerId, id);
        if (changed != 1) throw conflict("Request is not awaiting review");
        audit(officerId, id, true, "PRE_CLOSURE_ASSIGNED", null);
    }

    @Transactional
    public void approve(long officerId, long id) {
        long borrower = officerRequest(officerId, id);
        var record = get(borrower, id);
        if ("APPROVED".equals(record.status()) && Long.valueOf(officerId).equals(record.officerId())) return;
        int changed = jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST SET STATUS = 'APPROVED', OFFICER_ID = ?, UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ? AND STATUS IN ('PENDING','UNDER_REVIEW')
              AND EXISTS (SELECT 1 FROM LOAN_ACCOUNT l WHERE l.LOAN_ID = LOAN_CLOSURE_REQUEST.LOAN_ID AND l.LOAN_STATUS = 'ACTIVE')
            """, officerId, id);
        if (changed != 1) throw conflict("Only unpaid requests awaiting review can be approved");
        audit(officerId, id, true, "PRE_CLOSURE_APPROVED", null);
    }

    @Transactional
    public void reject(long officerId, long id, String reason) {
        validateReason(reason, true);
        officerRequest(officerId, id);
        int changed = jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST SET STATUS = 'REJECTED', OFFICER_ID = ?, UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ? AND STATUS IN ('PENDING','UNDER_REVIEW')
              AND NOT EXISTS (SELECT 1 FROM PAYMENT p JOIN SETTLEMENT_QUOTE q ON q.QUOTE_ID = p.QUOTE_ID
                  WHERE q.CLOSURE_REQUEST_ID = LOAN_CLOSURE_REQUEST.CLOSURE_REQUEST_ID)
            """, officerId, id);
        if (changed != 1) throw conflict("Only unpaid requests awaiting review can be rejected");
        audit(officerId, id, true, "PRE_CLOSURE_REJECTED", reason);
    }

    public List<Long> pendingRequests(long officerId, boolean includeHistory) {
        return jdbc.queryForList("""
            SELECT c.CLOSURE_REQUEST_ID FROM LOAN_CLOSURE_REQUEST c JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            JOIN OFFICER o ON o.OFFICER_ID = ? AND o.ROLE = 'LOAN_CLOSURE_OFFICER' AND o.ACCOUNT_STATUS = 'ACTIVE'
            WHERE c.CLOSURE_TYPE = 'FORECLOSURE'
              AND (? = 1 OR c.STATUS IN ('PENDING','UNDER_REVIEW','APPROVED','PAYMENT_PENDING'))
              AND (c.OFFICER_ID IS NULL OR c.OFFICER_ID = o.OFFICER_ID)
              AND (o.USER_ID IS NULL OR o.USER_ID <> l.BORROWER_ID) ORDER BY c.CREATED_AT DESC
            """, Long.class, officerId, includeHistory ? 1 : 0);
    }

    @Transactional
    public void complete(long borrowerId, long id, long loanId, long quoteId, long paymentId) {
        lockOwned(borrowerId, id);
        var record = get(borrowerId, id);
        if ("COMPLETED".equals(record.status())) return;
        Integer confirmed = jdbc.queryForObject("""
            SELECT COUNT(*) FROM LOAN_CLOSURE_REQUEST c JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            JOIN SETTLEMENT_QUOTE q ON q.CLOSURE_REQUEST_ID = c.CLOSURE_REQUEST_ID AND q.LOAN_ID = l.LOAN_ID
            JOIN PAYMENT p ON p.QUOTE_ID = q.QUOTE_ID
            WHERE c.CLOSURE_REQUEST_ID = ? AND l.LOAN_ID = ? AND q.QUOTE_ID = ? AND p.PAYMENT_ID = ?
              AND c.CLOSURE_TYPE = 'FORECLOSURE' AND c.STATUS = 'PAYMENT_PENDING'
              AND l.LOAN_STATUS = 'CLOSED' AND l.OUTSTANDING_AMOUNT = 0 AND l.CLOSURE_DATE IS NOT NULL
              AND q.QUOTE_TYPE = 'CLOSURE' AND q.QUOTE_STATUS = 'PAID' AND p.PAYMENT_STATUS = 'SUCCESS'
              AND EXISTS (SELECT 1 FROM AUDIT_LOG a WHERE a.PAYMENT_ID = p.PAYMENT_ID AND a.QUOTE_ID = q.QUOTE_ID
                  AND a.CLOSURE_REQUEST_ID = c.CLOSURE_REQUEST_ID AND a.ACTION = 'PRE_CLOSURE_LOAN_CLOSED')
            """, Integer.class, id, loanId, quoteId, paymentId);
        if (confirmed == null || confirmed != 1) throw conflict("LoanAccount has not confirmed this closure");
        int changed = jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST SET STATUS = 'COMPLETED', UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ? AND STATUS = 'PAYMENT_PENDING'
            """, id);
        if (changed != 1) throw conflict("Request changed during completion");
        audit(borrowerId, id, false, "PRE_CLOSURE_COMPLETED", "paymentId=" + paymentId + ";quoteId=" + quoteId);
    }

    // JWT identity and active-account checks.
    public long borrower(Jwt jwt) { return identity(jwt, "BORROWER", "BORROWER"); }
    public long officer(Jwt jwt) { return identity(jwt, "OFFICER", "LOAN_CLOSURE_OFFICER"); }

    private long identity(Jwt jwt, String type, String role) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (!type.equals(jwt.getClaimAsString("accountType")) || !role.equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account role is not permitted");
        }
        long id;
        try {
            Object claim = jwt.getClaim("userId");
            if (!(claim instanceof Number)) throw new IllegalArgumentException();
            id = new BigDecimal(claim.toString()).longValueExact();
            if (id <= 0 || !(type + ":" + id).equals(jwt.getSubject())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated identity");
        }
        Integer count = "BORROWER".equals(type)
            ? jdbc.queryForObject("SELECT COUNT(*) FROM BORROWERS WHERE USER_ID = ? AND ACCOUNT_STATUS = 'ACTIVE'", Integer.class, id)
            : jdbc.queryForObject("SELECT COUNT(*) FROM OFFICER WHERE OFFICER_ID = ? AND ACCOUNT_STATUS = 'ACTIVE' AND ROLE = 'LOAN_CLOSURE_OFFICER'", Integer.class, id);
        if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active or authorized");
        return id;
    }

    public record PreClosureRequest(
        Long loanId,
        String reason
    ) {}

    public record PreClosureQuote(
        Long quoteId,
        Long closureRequestId,
        Long loanId,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal chargesAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String quoteStatus,
        LocalDateTime validUntil,
        boolean expired,
        CalculationBasis calculation,
        boolean legacyFeePolicy
    ) {}

    public record CalculationBasis(LocalDate interestPaidThrough, LocalDate settlementDate, long interestDays,
        BigDecimal annualInterestRate, String dayCountBasis, String chargeType, BigDecimal chargeValue,
        BigDecimal taxRate, String chargePolicyReason) {}

    public record PreClosureRecord(
        Long closureRequestId,
        Long loanId,
        String closureType,
        String status,
        String reason,
        Long officerId,
        LocalDateTime createdAt,
        PreClosureQuote quote,
        LoanSummary loan,
        String decisionRemarks
    ) {}

    public record LoanSummary(Long borrowerId, String borrowerName, String loanReference, String productType,
        BigDecimal principalAmount, BigDecimal outstandingAmount, BigDecimal interestRate, String loanStatus,
        LocalDateTime maturityDate, LocalDateTime closureDate) {}
}
