
package com.clearclose.exit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.exit.dto.SettlementQuoteDto;

@Service
public class SettlementQuoteService {

    private final JdbcTemplate jdbc;

    // Quote is valid for 15 minutes
    private static final int QUOTE_VALIDITY_MINUTES = 15;

    public SettlementQuoteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -----------------------------------------
    // INTERNAL REQUEST DATA
    // -----------------------------------------

    private record RequestInfo(
        Long requestId,
        Long loanId,
        String requestStatus,
        String loanStatus,
        BigDecimal outstandingAmount,
        BigDecimal interestRate,
        LocalDateTime disbursementDate,
        int coolingOffDays,
        BigDecimal processingFee,
        LocalDateTime requestExpiry,
        LocalDateTime currentTime
    ) {}

    // -----------------------------------------
    // DATE CONVERSION
    // -----------------------------------------

    private LocalDateTime toDateTime(
        ResultSet rs,
        String column
    ) throws SQLException {

        Timestamp timestamp = rs.getTimestamp(column);

        return timestamp == null
            ? null
            : timestamp.toLocalDateTime();
    }

    // -----------------------------------------
    // QUOTE DATABASE QUERY
    // -----------------------------------------

    private static final String QUOTE_QUERY = """
        SELECT
            q.QUOTE_ID,
            q.LOAN_ID,
            q.EXIT_REQUEST_ID,
            q.QUOTE_TYPE,
            q.PRINCIPAL_AMOUNT,
            q.INTEREST_AMOUNT,
            q.CHARGES_AMOUNT,
            q.TAX_AMOUNT,
            q.TOTAL_AMOUNT,
            q.QUOTE_STATUS,
            q.VALID_UNTIL,
            q.ACCEPTED_AT,
            q.CREATED_AT,
            CASE
                WHEN q.VALID_UNTIL <= SYSDATE THEN 1
                ELSE 0
            END AS IS_EXPIRED
        FROM SETTLEMENT_QUOTE q
        JOIN LOAN_ACCOUNT l
            ON q.LOAN_ID = l.LOAN_ID
        WHERE l.BORROWER_ID = ?
          AND q.QUOTE_TYPE = 'COOLING_OFF'
        """;

    // -----------------------------------------
    // CONVERT DATABASE ROW TO DTO
    // -----------------------------------------

    private final RowMapper<SettlementQuoteDto> quoteMapper =
        (rs, rowNum) -> new SettlementQuoteDto(

            rs.getLong("QUOTE_ID"),

            rs.getLong("LOAN_ID"),

            rs.getLong("EXIT_REQUEST_ID"),

            rs.getString("QUOTE_TYPE"),

            rs.getBigDecimal("PRINCIPAL_AMOUNT"),

            rs.getBigDecimal("INTEREST_AMOUNT"),

            rs.getBigDecimal("CHARGES_AMOUNT"),

            rs.getBigDecimal("TAX_AMOUNT"),

            rs.getBigDecimal("TOTAL_AMOUNT"),

            rs.getString("QUOTE_STATUS"),

            toDateTime(rs, "VALID_UNTIL"),

            toDateTime(rs, "ACCEPTED_AT"),

            toDateTime(rs, "CREATED_AT"),

            rs.getInt("IS_EXPIRED") == 1
        );

    // =========================================
    // 1. CREATE OR GET SETTLEMENT QUOTE
    // =========================================

    @Transactional
    public SettlementQuoteDto createQuote(
        Long borrowerId,
        Long requestId
    ) {

        // Step 1: Get the exit request and loan details.
        // Lock the request row to prevent duplicate
        // quote creation by simultaneous requests.

        String requestSql = """
            SELECT
                c.EXIT_REQUEST_ID,
                c.LOAN_ID,
                c.STATUS AS REQUEST_STATUS,
                c.EXPIRY_DATE AS REQUEST_EXPIRY,
                l.LOAN_STATUS,
                l.OUTSTANDING_AMOUNT,
                l.INTEREST_RATE,
                l.DISBURSEMENT_DATE,
                r.COOLING_OFF_DAYS,
                r.COOLING_OFF_PROCESSING_FEE,
                SYSDATE AS CURRENT_DB_TIME
            FROM COOLING_OFF_REQUEST c
            JOIN LOAN_ACCOUNT l
                ON c.LOAN_ID = l.LOAN_ID
            JOIN LOAN_PRODUCT_RULE r
                ON l.RULE_ID = r.RULE_ID
            WHERE c.EXIT_REQUEST_ID = ?
              AND l.BORROWER_ID = ?
            FOR UPDATE OF c.STATUS
            """;

        List<RequestInfo> requests = jdbc.query(
            requestSql,
            (rs, rowNum) -> new RequestInfo(

                rs.getLong("EXIT_REQUEST_ID"),

                rs.getLong("LOAN_ID"),

                rs.getString("REQUEST_STATUS"),

                rs.getString("LOAN_STATUS"),

                rs.getBigDecimal("OUTSTANDING_AMOUNT"),

                rs.getBigDecimal("INTEREST_RATE"),

                toDateTime(rs, "DISBURSEMENT_DATE"),

                rs.getInt("COOLING_OFF_DAYS"),

                rs.getBigDecimal("COOLING_OFF_PROCESSING_FEE"),

                toDateTime(rs, "REQUEST_EXPIRY"),

                toDateTime(rs, "CURRENT_DB_TIME")
            ),
            requestId,
            borrowerId
        );

        if (requests.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cooling-off request not found"
            );
        }

        RequestInfo request = requests.get(0);

        // Step 2: Validate request status

        if (!List.of(
            "PENDING",
            "UNDER_REVIEW",
            "APPROVED"
        ).contains(request.requestStatus())) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request is not available for settlement"
            );
        }

        // Step 3: Validate loan status

        if (!"ACTIVE".equals(request.loanStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Loan is not active"
            );
        }

        // Step 4: Validate disbursement and rules

        if (request.disbursementDate() == null
                || request.coolingOffDays() <= 0) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invalid cooling-off loan details"
            );
        }

        LocalDateTime now = request.currentTime();

        LocalDateTime expiry =
            request.disbursementDate()
                .plusDays(request.coolingOffDays());

        // Respect an earlier recorded request deadline
        if (request.requestExpiry() != null
                && request.requestExpiry().isBefore(expiry)) {

            expiry = request.requestExpiry();
        }

        // Step 5: Check cooling-off deadline

        if (now.isBefore(request.disbursementDate())
                || !now.isBefore(expiry)) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cooling-off period has expired or is not yet valid"
            );
        }

        // Step 6: Validate outstanding principal

        BigDecimal principal = request.outstandingAmount();

        if (principal == null
                || principal.compareTo(BigDecimal.ZERO) <= 0) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No outstanding principal available"
            );
        }

        if (request.interestRate() == null
                || request.interestRate()
                    .compareTo(BigDecimal.ZERO) < 0) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invalid interest rate"
            );
        }

        // Step 7: Check whether a quote already exists

        List<SettlementQuoteDto> existingQuotes =
            jdbc.query(
                QUOTE_QUERY + """
                    AND q.EXIT_REQUEST_ID = ?
                    ORDER BY q.QUOTE_ID DESC
                    """,
                quoteMapper,
                borrowerId,
                requestId
            );

        if (!existingQuotes.isEmpty()) {

            SettlementQuoteDto latest = existingQuotes.get(0);

            // Do not generate another quote if paid
            // or already accepted.
            if ("PAID".equals(latest.quoteStatus())
                    || "ACCEPTED".equals(latest.quoteStatus())) {

                return latest;
            }

            // Reuse an available, unexpired quote
            if ("AVAILABLE".equals(latest.quoteStatus())
                    && !latest.expired()) {

                return latest;
            }

            // Mark an expired available quote
            if ("AVAILABLE".equals(latest.quoteStatus())
                    && latest.expired()) {

                jdbc.update(
                    """
                    UPDATE SETTLEMENT_QUOTE
                    SET QUOTE_STATUS = 'EXPIRED',
                        UPDATED_AT = CURRENT_TIMESTAMP
                    WHERE QUOTE_ID = ?
                    """,
                    latest.quoteId()
                );
            }
        }

        // Step 8: Calculate proportionate interest

        long elapsedSeconds = Duration.between(
            request.disbursementDate(),
            now
        ).getSeconds();

        // Formula:
        // principal * annual rate * elapsed seconds
        // ------------------------------------------
        //          100 * 365 * 24 * 60 * 60

        BigDecimal denominator =
            BigDecimal.valueOf(100L * 365 * 24 * 60 * 60);

        BigDecimal interest = principal
            .multiply(request.interestRate())
            .multiply(BigDecimal.valueOf(elapsedSeconds))
            .divide(
                denominator,
                2,
                RoundingMode.HALF_UP
            );

        principal = principal.setScale(
            2,
            RoundingMode.HALF_UP
        );

        // Demo policy: one upfront-disclosed processing fee, not previously collected.
        // This is not an exit penalty; do not reuse regular foreclosure charge rules.
        // Snapshot the linked product rule's fee; later rule edits cannot reprice a quote.
        // Loaded from this loan's SQL product rule above. A schema DEFAULT is not
        // a runtime fee: zero, 125.50, 500, etc. are all honored without a fallback.
        BigDecimal processingFee = request.processingFee();
        if (processingFee == null || processingFee.signum() < 0
                || processingFee.stripTrailingZeros().scale() > 2
                || processingFee.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The loan product rule has an invalid cooling-off processing fee. Contact the bank.");
        }
        BigDecimal charges = processingFee.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);

        BigDecimal total = principal
            .add(interest)
            .add(charges)
            .add(tax);

        // Step 9: Calculate quote validity

        LocalDateTime validUntil =
            now.plusMinutes(QUOTE_VALIDITY_MINUTES);

        // Quote cannot remain valid after cooling-off expiry
        if (validUntil.isAfter(expiry)) {
            validUntil = expiry;
        }

        // Step 10: Insert quote into Oracle

        String insertSql = """
            INSERT INTO SETTLEMENT_QUOTE
            (
                LOAN_ID,
                EXIT_REQUEST_ID,
                CLOSURE_REQUEST_ID,
                QUOTE_TYPE,
                PRINCIPAL_AMOUNT,
                INTEREST_AMOUNT,
                CHARGES_AMOUNT,
                TAX_AMOUNT,
                TOTAL_AMOUNT,
                VALID_UNTIL,
                QUOTE_STATUS
            )
            VALUES (
                ?, ?, NULL, 'COOLING_OFF',
                ?, ?, ?, ?, ?, ?, 'AVAILABLE'
            )
            """;

        final BigDecimal finalPrincipal = principal;
        final LocalDateTime finalValidUntil = validUntil;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {

            PreparedStatement ps =
                connection.prepareStatement(
                    insertSql,
                    new String[]{"QUOTE_ID"}
                );

            ps.setLong(1, request.loanId());
            ps.setLong(2, request.requestId());
            ps.setBigDecimal(3, finalPrincipal);
            ps.setBigDecimal(4, interest);
            ps.setBigDecimal(5, charges);
            ps.setBigDecimal(6, tax);
            ps.setBigDecimal(7, total);
            ps.setTimestamp(
                8,
                Timestamp.valueOf(finalValidUntil)
            );

            return ps;

        }, keyHolder);

        // Step 11: Get generated quote ID

        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new IllegalStateException(
                "Failed to generate settlement quote ID"
            );
        }

        // Step 12: Return saved quote

        return getQuoteById(
            borrowerId,
            generatedId.longValue()
        );
    }

    // =========================================
    // 2. GET LATEST QUOTE FOR EXIT REQUEST
    // =========================================

    public SettlementQuoteDto getQuoteForRequest(
        Long borrowerId,
        Long requestId
    ) {

        List<SettlementQuoteDto> quotes = jdbc.query(
            QUOTE_QUERY + """
                AND q.EXIT_REQUEST_ID = ?
                ORDER BY q.QUOTE_ID DESC
                """,
            quoteMapper,
            borrowerId,
            requestId
        );

        if (quotes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Settlement quote not found"
            );
        }

        return quotes.get(0);
    }

    // =========================================
    // 3. GET QUOTE BY QUOTE ID
    // =========================================

    public SettlementQuoteDto getQuoteById(
        Long borrowerId,
        Long quoteId
    ) {

        List<SettlementQuoteDto> quotes = jdbc.query(
            QUOTE_QUERY + """
                AND q.QUOTE_ID = ?
                """,
            quoteMapper,
            borrowerId,
            quoteId
        );

        if (quotes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Settlement quote not found"
            );
        }

        return quotes.get(0);
    }
}
