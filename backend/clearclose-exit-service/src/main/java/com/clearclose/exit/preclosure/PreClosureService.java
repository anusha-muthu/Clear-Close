package com.clearclose.exit.preclosure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PreClosureService {

    private final JdbcTemplate jdbc;

    private static final int QUOTE_MINUTES = 15;

    // Demo assumption: 18% tax on applicable foreclosure
    // charges. Replace with configured product/tax rules.
    private static final BigDecimal TAX_RATE =
            new BigDecimal("0.18");

    public PreClosureService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record LoanInfo(
        long loanId,
        String status,
        BigDecimal outstanding,
        String earlyClosureAllowed,
        String chargeType,
        BigDecimal chargeValue
    ) {}

    private ResponseStatusException error(
            HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private LoanInfo ownedLoan(long borrowerId, long loanId) {

        List<LoanInfo> rows = jdbc.query("""
            SELECT l.LOAN_ID, l.LOAN_STATUS,
                   l.OUTSTANDING_AMOUNT,
                   r.EARLY_CLOSURE_ALLOWED,
                   r.CLOSURE_CHARGE_TYPE,
                   r.CLOSURE_CHARGE_VALUE
            FROM LOAN_ACCOUNT l
            JOIN LOAN_PRODUCT_RULE r
              ON r.RULE_ID = l.RULE_ID
            WHERE l.LOAN_ID = ?
              AND l.BORROWER_ID = ?
            """,
            (rs, n) -> new LoanInfo(
                rs.getLong("LOAN_ID"),
                rs.getString("LOAN_STATUS"),
                rs.getBigDecimal("OUTSTANDING_AMOUNT"),
                rs.getString("EARLY_CLOSURE_ALLOWED"),
                rs.getString("CLOSURE_CHARGE_TYPE"),
                rs.getBigDecimal("CLOSURE_CHARGE_VALUE")
            ),
            loanId, borrowerId
        );

        if (rows.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,
                    "Loan not found for this borrower");
        }

        return rows.get(0);
    }

    private long generatedId(
            String sql, Object... values) {

        KeyHolder holder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                sql, new String[] {
                    sql.contains("LOAN_CLOSURE_REQUEST")
                        ? "CLOSURE_REQUEST_ID" : "QUOTE_ID"
                }
            );

            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }

            return ps;
        }, holder);

        if (holder.getKey() == null) {
            throw new IllegalStateException(
                    "Database did not return generated ID");
        }

        return holder.getKey().longValue();
    }

    private void auditBorrower(
            long borrowerId, long requestId, String action) {

        jdbc.update("""
            INSERT INTO AUDIT_LOG
                (USER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, CLOSURE_REQUEST_ID)
            VALUES (?, ?, 'LOAN_CLOSURE_REQUEST', ?, ?)
            """,
            borrowerId, requestId, action, requestId);
    }

    private void auditOfficer(
            long officerId, long requestId, String action) {

        jdbc.update("""
            INSERT INTO AUDIT_LOG
                (OFFICER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, CLOSURE_REQUEST_ID)
            VALUES (?, ?, 'LOAN_CLOSURE_REQUEST', ?, ?)
            """,
            officerId, requestId, action, requestId);
    }

    
    // CREATE REQUEST AND SETTLEMENT QUOTE
    

    @Transactional
    public PreClosureRecord create(
            long borrowerId, PreClosureRequest request) {

        if (request == null || request.loanId() == null
                || request.loanId() <= 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Valid loan ID is required");
        }

        // Lock loan before checking competing requests.
        List<Long> locked = jdbc.queryForList("""
            SELECT LOAN_ID FROM LOAN_ACCOUNT
            WHERE LOAN_ID = ? AND BORROWER_ID = ?
            FOR UPDATE
            """, Long.class, request.loanId(), borrowerId);

        if (locked.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND, "Loan not found");
        }

        LoanInfo loan = ownedLoan(
                borrowerId, request.loanId());

        if (!"ACTIVE".equals(loan.status())) {
            throw error(HttpStatus.CONFLICT,
                    "Only active loans can be pre-closed");
        }

        if (!"Y".equals(loan.earlyClosureAllowed())) {
            throw error(HttpStatus.CONFLICT,
                    "Early closure is not allowed");
        }

        if (loan.outstanding() == null ||
                loan.outstanding().signum() <= 0) {
            throw error(HttpStatus.CONFLICT,
                    "No outstanding principal");
        }

        // Avoid competing cooling-off and closure workflows.
        Integer cooling = jdbc.queryForObject("""
            SELECT COUNT(*) FROM COOLING_OFF_REQUEST
            WHERE LOAN_ID = ?
              AND STATUS IN
                  ('PENDING', 'UNDER_REVIEW',
                   'MORE_INFO_REQUIRED', 'APPROVED')
            """, Integer.class, loan.loanId());

        Integer closure = jdbc.queryForObject("""
            SELECT COUNT(*) FROM LOAN_CLOSURE_REQUEST
            WHERE LOAN_ID = ?
              AND STATUS IN
                  ('PENDING', 'UNDER_REVIEW',
                   'PAYMENT_PENDING', 'APPROVED')
            """, Integer.class, loan.loanId());

        if (cooling > 0 || closure > 0) {
            throw error(HttpStatus.CONFLICT,
                    "Another loan exit or closure request is active");
        }

        BigDecimal principal = loan.outstanding()
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal rate = loan.chargeValue();

        if (rate == null || rate.signum() < 0) {
            throw error(HttpStatus.CONFLICT,
                    "Invalid foreclosure charge rule");
        }

        BigDecimal charges;

        switch (loan.chargeType()) {
            case "NONE" ->
                charges = BigDecimal.ZERO;

            case "FIXED" ->
                charges = rate;

            case "PERCENTAGE" ->
                charges = principal.multiply(rate)
                    .divide(BigDecimal.valueOf(100),
                            2, RoundingMode.HALF_UP);

            default -> throw error(HttpStatus.CONFLICT,
                    "Unsupported foreclosure charge type");
        }

        charges = charges.setScale(
                2, RoundingMode.HALF_UP);

        // No accrued-interest ledger is present in
        // the supplied schema. Do not fabricate interest.
        BigDecimal interest = new BigDecimal("0.00");

        BigDecimal tax = charges.multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = principal
                .add(interest)
                .add(charges)
                .add(tax);

        String reason = request.reason();

        if (reason != null && reason.length() > 1000) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Reason must not exceed 1000 characters");
        }

        long requestId = generatedId("""
            INSERT INTO LOAN_CLOSURE_REQUEST
                (LOAN_ID, CLOSURE_TYPE, STATUS, REASON)
            VALUES (?, 'FORECLOSURE', 'PENDING', ?)
            """, loan.loanId(), reason);

        jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST
            SET STATUS = 'PAYMENT_PENDING',
                UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ?
            """, requestId);

        LocalDateTime validUntil =
                LocalDateTime.now().plusMinutes(QUOTE_MINUTES);

        generatedId("""
            INSERT INTO SETTLEMENT_QUOTE
                (LOAN_ID, EXIT_REQUEST_ID,
                 CLOSURE_REQUEST_ID, QUOTE_TYPE,
                 PRINCIPAL_AMOUNT, INTEREST_AMOUNT,
                 CHARGES_AMOUNT, TAX_AMOUNT,
                 TOTAL_AMOUNT, VALID_UNTIL,
                 QUOTE_STATUS)
            VALUES
                (?, NULL, ?, 'CLOSURE',
                 ?, ?, ?, ?, ?, ?, 'AVAILABLE')
            """,
            loan.loanId(),
            requestId,
            principal,
            interest,
            charges,
            tax,
            total,
            Timestamp.valueOf(validUntil)
        );

        auditBorrower(
                borrowerId, requestId, "PRE_CLOSURE_CREATED");

        return get(borrowerId, requestId);
    }

    // --------------------------------------------
    // FETCH REQUEST AND QUOTE
    // --------------------------------------------

    public PreClosureRecord get(
            long borrowerId, long requestId) {

        List<PreClosureRecord> rows = jdbc.query("""
            SELECT c.CLOSURE_REQUEST_ID, c.LOAN_ID,
                   c.CLOSURE_TYPE, c.STATUS, c.REASON,
                   c.OFFICER_ID, c.CREATED_AT
            FROM LOAN_CLOSURE_REQUEST c
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            WHERE c.CLOSURE_REQUEST_ID = ?
              AND c.CLOSURE_TYPE = 'FORECLOSURE'
              AND l.BORROWER_ID = ?
            """,
            (rs, n) -> {
                long id = rs.getLong("CLOSURE_REQUEST_ID");
                long officer = rs.getLong("OFFICER_ID");

                Timestamp created =
                        rs.getTimestamp("CREATED_AT");

                return new PreClosureRecord(
                    id,
                    rs.getLong("LOAN_ID"),
                    rs.getString("CLOSURE_TYPE"),
                    rs.getString("STATUS"),
                    rs.getString("REASON"),
                    rs.wasNull() ? null : officer,
                    created == null ? null
                        : created.toLocalDateTime(),
                    quoteForRequest(id)
                );
            },
            requestId, borrowerId
        );

        if (rows.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,
                    "Pre-closure request not found");
        }

        return rows.get(0);
    }

    private PreClosureQuote quoteForRequest(long requestId) {

        List<PreClosureQuote> quotes = jdbc.query("""
            SELECT QUOTE_ID, CLOSURE_REQUEST_ID,
                   LOAN_ID, PRINCIPAL_AMOUNT,
                   INTEREST_AMOUNT, CHARGES_AMOUNT,
                   TAX_AMOUNT, TOTAL_AMOUNT,
                   QUOTE_STATUS, VALID_UNTIL,
                   CASE WHEN VALID_UNTIL <= SYSDATE
                        THEN 1 ELSE 0 END AS EXPIRED
            FROM SETTLEMENT_QUOTE
            WHERE CLOSURE_REQUEST_ID = ?
              AND QUOTE_TYPE = 'CLOSURE'
            ORDER BY QUOTE_ID DESC
            """,
            (rs, n) -> new PreClosureQuote(
                rs.getLong("QUOTE_ID"),
                rs.getLong("CLOSURE_REQUEST_ID"),
                rs.getLong("LOAN_ID"),
                rs.getBigDecimal("PRINCIPAL_AMOUNT"),
                rs.getBigDecimal("INTEREST_AMOUNT"),
                rs.getBigDecimal("CHARGES_AMOUNT"),
                rs.getBigDecimal("TAX_AMOUNT"),
                rs.getBigDecimal("TOTAL_AMOUNT"),
                rs.getString("QUOTE_STATUS"),
                rs.getTimestamp("VALID_UNTIL")
                    .toLocalDateTime(),
                rs.getInt("EXPIRED") == 1
            ),
            requestId
        );

        return quotes.isEmpty() ? null : quotes.get(0);
    }

    
    // ACCEPT QUOTE
    

    @Transactional
    public PreClosureRecord accept(
            long borrowerId, long requestId) {

        PreClosureRecord record = get(borrowerId, requestId);

        if (!"PAYMENT_PENDING".equals(record.status())) {
            throw error(HttpStatus.CONFLICT,
                    "Request is not awaiting payment");
        }

        PreClosureQuote quote = record.quote();

        if (quote == null) {
            throw error(HttpStatus.NOT_FOUND,
                    "Settlement quote not found");
        }

        int changed = jdbc.update("""
            UPDATE SETTLEMENT_QUOTE
            SET QUOTE_STATUS = 'ACCEPTED',
                ACCEPTED_AT = SYSTIMESTAMP,
                UPDATED_AT = SYSTIMESTAMP
            WHERE QUOTE_ID = ?
              AND QUOTE_STATUS = 'AVAILABLE'
              AND VALID_UNTIL > SYSDATE
            """, quote.quoteId());

        if (changed != 1) {
            throw error(HttpStatus.CONFLICT,
                    "Quote is expired or already processed");
        }

        auditBorrower(
                borrowerId, requestId, "QUOTE_ACCEPTED");

        return get(borrowerId, requestId);
    }

    
    // OFFICER APPROVAL
   

    @Transactional
    public void approve(long officerId, long requestId) {

        // Lock the loan first to serialize closure operations.
        List<Long> loanIds = jdbc.queryForList("""
            SELECT LOAN_ID FROM LOAN_CLOSURE_REQUEST
            WHERE CLOSURE_REQUEST_ID = ?
              AND CLOSURE_TYPE = 'FORECLOSURE'
            """, Long.class, requestId);

        if (loanIds.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,
                    "Pre-closure request not found");
        }

        long loanId = loanIds.get(0);

        List<Long> locked = jdbc.queryForList("""
            SELECT LOAN_ID FROM LOAN_ACCOUNT
            WHERE LOAN_ID = ? AND LOAN_STATUS = 'ACTIVE'
            FOR UPDATE
            """, Long.class, loanId);

        if (locked.isEmpty()) {
            throw error(HttpStatus.CONFLICT,
                    "Loan is not active");
        }

        List<String> statuses = jdbc.queryForList("""
            SELECT STATUS FROM LOAN_CLOSURE_REQUEST
            WHERE CLOSURE_REQUEST_ID = ?
              AND LOAN_ID = ?
              AND CLOSURE_TYPE = 'FORECLOSURE'
            FOR UPDATE
            """, String.class, requestId, loanId);

        if (statuses.isEmpty() ||
                !"UNDER_REVIEW".equals(statuses.get(0))) {
            throw error(HttpStatus.CONFLICT,
                    "Request is not awaiting officer review");
        }

        Integer paid = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM PAYMENT p
            JOIN SETTLEMENT_QUOTE q
              ON q.QUOTE_ID = p.QUOTE_ID
            WHERE q.CLOSURE_REQUEST_ID = ?
              AND q.QUOTE_TYPE = 'CLOSURE'
              AND p.PAYMENT_STATUS = 'SUCCESS'
              AND p.AMOUNT = q.TOTAL_AMOUNT
            """, Integer.class, requestId);

        if (paid == null || paid != 1) {
            throw error(HttpStatus.CONFLICT,
                    "Exactly one verified full payment is required");
        }

        int updated = jdbc.update("""
            UPDATE LOAN_ACCOUNT
            SET LOAN_STATUS = 'CLOSED',
                OUTSTANDING_AMOUNT = 0,
                CLOSURE_DATE = SYSDATE,
                UPDATED_AT = SYSTIMESTAMP
            WHERE LOAN_ID = ?
              AND LOAN_STATUS = 'ACTIVE'
            """, loanId);

        if (updated != 1) {
            throw error(HttpStatus.CONFLICT,
                    "Loan closure could not be completed");
        }

        jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST
            SET STATUS = 'COMPLETED',
                OFFICER_ID = ?,
                UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ?
            """, officerId, requestId);

        auditOfficer(
                officerId, requestId, "PRE_CLOSURE_APPROVED");
    }

   
    // OFFICER REJECTION
    

    @Transactional
    public void reject(
            long officerId, long requestId, String reason) {

        if (reason == null || reason.isBlank()
                || reason.length() > 1000) {
            throw error(HttpStatus.BAD_REQUEST,
                    "A rejection reason of up to 1000 characters is required");
        }

        // Reject only before a successful payment.
        Integer paid = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM PAYMENT p
            JOIN SETTLEMENT_QUOTE q
              ON q.QUOTE_ID = p.QUOTE_ID
            WHERE q.CLOSURE_REQUEST_ID = ?
              AND p.PAYMENT_STATUS = 'SUCCESS'
            """, Integer.class, requestId);

        if (paid != null && paid > 0) {
            throw error(HttpStatus.CONFLICT,
                    "Paid requests require manual review or refund handling");
        }

        int changed = jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST
            SET STATUS = 'REJECTED',
                REASON = ?,
                OFFICER_ID = ?,
                UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ?
              AND CLOSURE_TYPE = 'FORECLOSURE'
              AND STATUS IN ('PENDING', 'PAYMENT_PENDING')
            """, reason, officerId, requestId);

        if (changed != 1) {
            throw error(HttpStatus.CONFLICT,
                    "Request cannot be rejected in its current state");
        }

        auditOfficer(
                officerId, requestId, "PRE_CLOSURE_REJECTED");
    }

    
    // OFFICER DASHBOARD
    

    public List<Long> pendingRequests() {
        return jdbc.queryForList("""
            SELECT CLOSURE_REQUEST_ID
            FROM LOAN_CLOSURE_REQUEST
            WHERE CLOSURE_TYPE = 'FORECLOSURE'
              AND STATUS IN
                  ('PENDING', 'PAYMENT_PENDING', 'UNDER_REVIEW')
            ORDER BY CREATED_AT DESC
            """, Long.class);
    }
}