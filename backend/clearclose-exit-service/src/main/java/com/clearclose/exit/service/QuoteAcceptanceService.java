
package com.clearclose.exit.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.exit.dto.SettlementQuoteDto;

@Service
public class QuoteAcceptanceService {

    private final JdbcTemplate jdbc;
    private final SettlementQuoteService quoteService;

    public QuoteAcceptanceService(
        JdbcTemplate jdbc,
        SettlementQuoteService quoteService
    ) {
        this.jdbc = jdbc;
        this.quoteService = quoteService;
    }

    // -----------------------------------------
    // INTERNAL DATABASE RECORD
    // -----------------------------------------

    private record QuoteInfo(
        Long requestId,
        String quoteStatus,
        String requestStatus,
        String loanStatus,
        LocalDateTime validUntil,
        LocalDateTime currentTime
    ) {}

    // =========================================
    // ACCEPT SETTLEMENT QUOTE
    // =========================================

    @Transactional
    public SettlementQuoteDto acceptQuote(
        Long borrowerId,
        Long quoteId
    ) {

        // 1. Validate quote ID
        if (quoteId == null || quoteId <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Valid quote ID is required"
            );
        }

        // 2. Lock the related exit request.
        // This serializes acceptance and quote
        // generation for the same request.

        String lockSql = """
            SELECT c.EXIT_REQUEST_ID
            FROM COOLING_OFF_REQUEST c
            WHERE c.EXIT_REQUEST_ID = (
                SELECT q.EXIT_REQUEST_ID
                FROM SETTLEMENT_QUOTE q
                WHERE q.QUOTE_ID = ?
                  AND q.QUOTE_TYPE = 'COOLING_OFF'
            )
            FOR UPDATE
            """;

        List<Long> lockedRequests = jdbc.query(
            lockSql,
            (rs, rowNum) ->
                rs.getLong("EXIT_REQUEST_ID"),
            quoteId
        );

        if (lockedRequests.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cooling-off quote not found"
            );
        }

        // 3. Get quote, request and loan details.
        // Borrower ID comes from the verified JWT.

        String sql = """
            SELECT
                q.EXIT_REQUEST_ID,
                q.QUOTE_STATUS,
                q.VALID_UNTIL,
                c.STATUS AS REQUEST_STATUS,
                l.LOAN_STATUS,
                SYSDATE AS CURRENT_DB_TIME

            FROM SETTLEMENT_QUOTE q

            JOIN COOLING_OFF_REQUEST c
                ON q.EXIT_REQUEST_ID = c.EXIT_REQUEST_ID

            JOIN LOAN_ACCOUNT l
                ON c.LOAN_ID = l.LOAN_ID

            WHERE q.QUOTE_ID = ?
              AND q.QUOTE_TYPE = 'COOLING_OFF'
              AND q.LOAN_ID = c.LOAN_ID
              AND l.BORROWER_ID = ?
            """;

        List<QuoteInfo> quotes = jdbc.query(
            sql,
            (rs, rowNum) -> {

                Timestamp validUntil =
                    rs.getTimestamp("VALID_UNTIL");

                Timestamp currentTime =
                    rs.getTimestamp("CURRENT_DB_TIME");

                return new QuoteInfo(
                    rs.getLong("EXIT_REQUEST_ID"),
                    rs.getString("QUOTE_STATUS"),
                    rs.getString("REQUEST_STATUS"),
                    rs.getString("LOAN_STATUS"),
                    validUntil == null
                        ? null
                        : validUntil.toLocalDateTime(),
                    currentTime.toLocalDateTime()
                );
            },
            quoteId,
            borrowerId
        );

        // 4. Check borrower ownership
        if (quotes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Quote not found for this borrower"
            );
        }

        QuoteInfo quote = quotes.get(0);

        // 5. If already accepted, return same quote
        if ("ACCEPTED".equals(quote.quoteStatus())) {
            return quoteService.getQuoteById(
                borrowerId,
                quoteId
            );
        }

        // 6. Officer must have approved the request
        if (!"APPROVED".equals(quote.requestStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Officer approval is required"
            );
        }

        // 7. Loan must still be active
        if (!"ACTIVE".equals(quote.loanStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Loan is not active"
            );
        }

        // 8. Quote must be available
        if (!"AVAILABLE".equals(quote.quoteStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Quote is not available for acceptance"
            );
        }

        // 9. Quote must not be expired
        if (quote.validUntil() == null ||
            !quote.currentTime().isBefore(
                quote.validUntil()
            )) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Quote has expired. Generate a new quote."
            );
        }

        // 10. Only the latest quote can be accepted
        Long latestQuoteId = jdbc.queryForObject(
            """
            SELECT MAX(QUOTE_ID)
            FROM SETTLEMENT_QUOTE
            WHERE EXIT_REQUEST_ID = ?
              AND QUOTE_TYPE = 'COOLING_OFF'
            """,
            Long.class,
            quote.requestId()
        );

        if (!quoteId.equals(latestQuoteId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A newer settlement quote exists"
            );
        }

        // 11. Prevent multiple accepted quotes
        Integer acceptedCount = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM SETTLEMENT_QUOTE
            WHERE EXIT_REQUEST_ID = ?
              AND QUOTE_ID <> ?
              AND QUOTE_STATUS IN ('ACCEPTED', 'PAID')
            """,
            Integer.class,
            quote.requestId(),
            quoteId
        );

        if (acceptedCount != null && acceptedCount > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Another quote is already accepted or paid"
            );
        }

        // 12. Accept the quote
        int updated = jdbc.update(
            """
            UPDATE SETTLEMENT_QUOTE
            SET QUOTE_STATUS = 'ACCEPTED',
                ACCEPTED_AT = SYSTIMESTAMP,
                UPDATED_AT = SYSTIMESTAMP
            WHERE QUOTE_ID = ?
              AND QUOTE_STATUS = 'AVAILABLE'
              AND VALID_UNTIL > SYSDATE
            """,
            quoteId
        );

        if (updated != 1) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Quote could not be accepted"
            );
        }

        // 13. Record borrower acceptance in audit log
        jdbc.update(
            """
            INSERT INTO AUDIT_LOG
            (
                USER_ID,
                ENTITY_ID,
                ENTITY_TYPE,
                ACTION,
                DETAILS,
                QUOTE_ID
            )
            VALUES
            (
                ?, ?,
                'SETTLEMENT_QUOTE',
                'QUOTE_ACCEPTED',
                'Borrower accepted cooling-off settlement quote',
                ?
            )
            """,
            borrowerId,
            quoteId,
            quoteId
        );

        // 14. Return updated quote
        return quoteService.getQuoteById(
            borrowerId,
            quoteId
        );
    }
}
