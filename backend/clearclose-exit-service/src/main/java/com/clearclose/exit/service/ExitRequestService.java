
package com.clearclose.exit.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
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

import com.clearclose.exit.dto.EligibilityDto;
import com.clearclose.exit.dto.ExitRequestDto.CreateRequest;
import com.clearclose.exit.dto.ExitRequestDto.RequestResponse;

@Service
public class ExitRequestService {

    private final JdbcTemplate jdbc;
    private final EligibilityService eligibilityService;

    public ExitRequestService(
        JdbcTemplate jdbc,
        EligibilityService eligibilityService
    ) {
        this.jdbc = jdbc;
        this.eligibilityService = eligibilityService;
    }

    // ---------------------------------------
    // COMMON SELECT QUERY
    // ---------------------------------------

    private static final String REQUEST_QUERY = """
        SELECT
            c.EXIT_REQUEST_ID,
            c.LOAN_ID,
            l.LOAN_REFERENCE,
            c.OFFICER_ID,
            c.STATUS,
            c.REASON,
            c.REQUEST_DATE,
            c.EXPIRY_DATE,
            c.DECIDED_AT,
            c.CREATED_AT,
            c.UPDATED_AT
        FROM COOLING_OFF_REQUEST c
        JOIN LOAN_ACCOUNT l
            ON c.LOAN_ID = l.LOAN_ID
        WHERE l.BORROWER_ID = ?
        """;

    // ---------------------------------------
    // CONVERT DATABASE TIMESTAMP
    // ---------------------------------------

    private LocalDateTime toDateTime(
        ResultSet rs,
        String column
    ) throws SQLException {

        Timestamp timestamp = rs.getTimestamp(column);

        return timestamp == null
            ? null
            : timestamp.toLocalDateTime();
    }

    // ---------------------------------------
    // CONVERT DATABASE ROW TO DTO
    // ---------------------------------------

    private final RowMapper<RequestResponse> requestMapper =
        (rs, rowNum) -> {

            Object officerValue =
                rs.getObject("OFFICER_ID");

            Long officerId = officerValue == null
                ? null
                : ((Number) officerValue).longValue();

            return new RequestResponse(
                rs.getLong("EXIT_REQUEST_ID"),
                rs.getLong("LOAN_ID"),
                rs.getString("LOAN_REFERENCE"),
                officerId,
                rs.getString("STATUS"),
                rs.getString("REASON"),
                toDateTime(rs, "REQUEST_DATE"),
                toDateTime(rs, "EXPIRY_DATE"),
                toDateTime(rs, "DECIDED_AT"),
                toDateTime(rs, "CREATED_AT"),
                toDateTime(rs, "UPDATED_AT")
            );
        };

    // =======================================
    // 1. CREATE COOLING-OFF EXIT REQUEST
    // =======================================

    @Transactional
    public RequestResponse createRequest(
        Long borrowerId,
        CreateRequest request
    ) {

        // Step 1: Validate loan ID
        if (request == null ||
            request.loanId() == null ||
            request.loanId() <= 0) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Valid loan ID is required"
            );
        }

        // Step 2: Validate reason
        String cleanedReason = request.reason() == null
            ? null
            : request.reason().trim();

        if (cleanedReason != null &&
            cleanedReason.length() > 1000) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Reason cannot exceed 1000 characters"
            );
        }

        if (cleanedReason != null &&
            cleanedReason.isEmpty()) {
            cleanedReason = null;
        }

        final String reason = cleanedReason;

        // Step 3: Lock the loan row while processing
        // This prevents two simultaneous requests
        // through this service from creating duplicates.

        String lockSql = """
            SELECT LOAN_ID
            FROM LOAN_ACCOUNT
            WHERE LOAN_ID = ?
              AND BORROWER_ID = ?
            FOR UPDATE
            """;

        List<Long> loanIds = jdbc.query(
            lockSql,
            (rs, rowNum) -> rs.getLong("LOAN_ID"),
            request.loanId(),
            borrowerId
        );

        if (loanIds.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Loan not found"
            );
        }

        // The loan lock also serializes creation against foreclosure requests.
        Integer activeClosure = jdbc.queryForObject("""
            SELECT COUNT(*) FROM LOAN_CLOSURE_REQUEST WHERE LOAN_ID = ?
              AND STATUS IN ('PENDING', 'UNDER_REVIEW', 'APPROVED', 'PAYMENT_PENDING')
            """, Integer.class, request.loanId());
        if (activeClosure != null && activeClosure > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Another loan closure request is already active");
        }

        // Step 4: Reuse Step 19 eligibility logic
        EligibilityDto eligibility =
            eligibilityService.checkEligibility(
                request.loanId(),
                borrowerId
            );

        // Step 5: Reject ineligible requests
        if (!eligibility.eligible()) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                eligibility.reason()
            );
        }

        // Step 6: Get calculated expiry
        Timestamp expiry = Timestamp.valueOf(
            eligibility.expiryDate()
        );

        // Step 7: Insert request
        String insertSql = """
            INSERT INTO COOLING_OFF_REQUEST
            (
                LOAN_ID,
                REQUEST_DATE,
                EXPIRY_DATE,
                STATUS,
                REASON
            )
            VALUES (?, SYSDATE, ?, 'PENDING', ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {

            PreparedStatement ps =
                connection.prepareStatement(
                    insertSql,
                    new String[]{"EXIT_REQUEST_ID"}
                );

            ps.setLong(1, request.loanId());
            ps.setTimestamp(2, expiry);

            if (reason == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, reason);
            }

            return ps;

        }, keyHolder);

        // Step 8: Retrieve generated request ID
        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new IllegalStateException(
                "Failed to generate exit request ID"
            );
        }

        Long exitRequestId = generatedId.longValue();

        // Step 9: Return created request
        return getRequestById(
            borrowerId,
            exitRequestId
        );
    }

    // =======================================
    // 2. GET ALL MY EXIT REQUESTS
    // =======================================

    public List<RequestResponse> getMyRequests(
        Long borrowerId
    ) {

        String sql = REQUEST_QUERY + """
            ORDER BY c.REQUEST_DATE DESC,
                     c.EXIT_REQUEST_ID DESC
            """;

        return jdbc.query(
            sql,
            requestMapper,
            borrowerId
        );
    }

    // =======================================
    // 3. GET REQUEST BY ID
    // =======================================

    public RequestResponse getRequestById(
        Long borrowerId,
        Long exitRequestId
    ) {

        String sql = REQUEST_QUERY + """
            AND c.EXIT_REQUEST_ID = ?
            """;

        List<RequestResponse> requests = jdbc.query(
            sql,
            requestMapper,
            borrowerId,
            exitRequestId
        );

        if (requests.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Exit request not found"
            );
        }

        return requests.get(0);
    }
}
