
package com.clearclose.exit.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.exit.dto.OfficerDashboardDto.DashboardSummary;
import com.clearclose.exit.dto.OfficerDashboardDto.RequestDetails;



@Service
public class OfficerDashboardService {

    private final JdbcTemplate jdbc;

    public OfficerDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -----------------------------------------
    // INTERNAL OFFICER DATA
    // -----------------------------------------

    private record OfficerInfo(
        Long officerId,
        Long linkedUserId,
        String role,
        String accountStatus
    ) {}

    private record RequestState(
        Long loanId,
        Long assignedOfficerId,
        String status
    ) {}

    // -----------------------------------------
    // HELPER: NULLABLE NUMBER
    // -----------------------------------------

    private Long nullableLong(
        ResultSet rs,
        String column
    ) throws SQLException {

        Number value = (Number) rs.getObject(column);

        return value == null
            ? null
            : value.longValue();
    }

    // -----------------------------------------
    // HELPER: DATABASE DATE
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
    // VERIFY OFFICER ACCOUNT
    // -----------------------------------------

    private OfficerInfo verifyOfficer(Long officerId) {

        String sql = """
            SELECT
                OFFICER_ID,
                USER_ID,
                ROLE,
                ACCOUNT_STATUS
            FROM OFFICER
            WHERE OFFICER_ID = ?
            """;

        List<OfficerInfo> officers = jdbc.query(
            sql,
            (rs, rowNum) -> new OfficerInfo(
                rs.getLong("OFFICER_ID"),
                nullableLong(rs, "USER_ID"),
                rs.getString("ROLE"),
                rs.getString("ACCOUNT_STATUS")
            ),
            officerId
        );

        if (officers.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Officer account not found"
            );
        }

        OfficerInfo officer = officers.get(0);

        if (!"ACTIVE".equals(officer.accountStatus())
                || !"LOAN_CLOSURE_OFFICER".equals(
                    officer.role()
                )) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Officer account is not authorized"
            );
        }

        return officer;
    }

    // -----------------------------------------
    // COMMON REQUEST QUERY
    // -----------------------------------------

    private static final String REQUEST_QUERY = """
        SELECT
            c.EXIT_REQUEST_ID,
            c.LOAN_ID,
            l.LOAN_REFERENCE,
            l.BORROWER_ID,
            b.NAME AS BORROWER_NAME,
            c.STATUS,
            c.REASON,
            c.OFFICER_ID,
            c.REQUEST_DATE,
            c.EXPIRY_DATE,
            l.PRINCIPAL_AMOUNT,
            l.OUTSTANDING_AMOUNT,
            l.INTEREST_RATE,
            q.QUOTE_ID,
            q.TOTAL_AMOUNT AS SETTLEMENT_AMOUNT,
            q.QUOTE_STATUS,
            q.VALID_UNTIL AS QUOTE_VALID_UNTIL

        FROM COOLING_OFF_REQUEST c

        JOIN LOAN_ACCOUNT l
            ON c.LOAN_ID = l.LOAN_ID

        JOIN BORROWERS b
            ON l.BORROWER_ID = b.USER_ID

        LEFT JOIN SETTLEMENT_QUOTE q
            ON q.QUOTE_ID = (
                SELECT MAX(q2.QUOTE_ID)
                FROM SETTLEMENT_QUOTE q2
                WHERE q2.EXIT_REQUEST_ID =
                    c.EXIT_REQUEST_ID
                  AND q2.QUOTE_TYPE = 'COOLING_OFF'
            )

        WHERE (
            c.OFFICER_ID IS NULL
            OR c.OFFICER_ID = ?
        )
        """;

    // -----------------------------------------
    // CONVERT DATABASE ROW INTO DTO
    // -----------------------------------------

    private final RowMapper<RequestDetails> requestMapper =
        (rs, rowNum) -> new RequestDetails(

            rs.getLong("EXIT_REQUEST_ID"),

            rs.getLong("LOAN_ID"),

            rs.getString("LOAN_REFERENCE"),

            rs.getLong("BORROWER_ID"),

            rs.getString("BORROWER_NAME"),

            rs.getString("STATUS"),

            rs.getString("REASON"),

            nullableLong(rs, "OFFICER_ID"),

            toDateTime(rs, "REQUEST_DATE"),

            toDateTime(rs, "EXPIRY_DATE"),

            rs.getBigDecimal("PRINCIPAL_AMOUNT"),

            rs.getBigDecimal("OUTSTANDING_AMOUNT"),

            rs.getBigDecimal("INTEREST_RATE"),

            nullableLong(rs, "QUOTE_ID"),

            rs.getBigDecimal("SETTLEMENT_AMOUNT"),

            rs.getString("QUOTE_STATUS"),

            toDateTime(rs, "QUOTE_VALID_UNTIL")
        );

    // =========================================
    // 1. GET VISIBLE COOLING-OFF REQUESTS
    // =========================================

    public List<RequestDetails> getAllRequests(
        Long officerId
    ) {

        verifyOfficer(officerId);

        String sql = REQUEST_QUERY + """
            ORDER BY c.REQUEST_DATE DESC,
                     c.EXIT_REQUEST_ID DESC
            """;

        return jdbc.query(
            sql,
            requestMapper,
            officerId
        );
    }

    // =========================================
    // 2. GET REQUEST DETAILS
    // =========================================

    public RequestDetails getRequestDetails(
        Long officerId,
        Long requestId
    ) {

        verifyOfficer(officerId);

        String sql = REQUEST_QUERY + """
            AND c.EXIT_REQUEST_ID = ?
            """;

        List<RequestDetails> requests = jdbc.query(
            sql,
            requestMapper,
            officerId,
            requestId
        );

        if (requests.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Request not found or not accessible"
            );
        }

        return requests.get(0);
    }

    // =========================================
    // 3. GET DASHBOARD SUMMARY
    // =========================================

    public DashboardSummary getSummary(Long officerId) {

        verifyOfficer(officerId);

        String sql = """
            SELECT
                COUNT(*) AS TOTAL_REQUESTS,

                NVL(SUM(CASE
                    WHEN STATUS = 'PENDING'
                    THEN 1 ELSE 0 END), 0) AS PENDING,

                NVL(SUM(CASE
                    WHEN STATUS = 'UNDER_REVIEW'
                    THEN 1 ELSE 0 END), 0) AS UNDER_REVIEW,

                NVL(SUM(CASE
                    WHEN STATUS = 'APPROVED'
                    THEN 1 ELSE 0 END), 0) AS APPROVED,

                NVL(SUM(CASE
                    WHEN STATUS = 'COMPLETED'
                    THEN 1 ELSE 0 END), 0) AS COMPLETED,

                NVL(SUM(CASE
                    WHEN OFFICER_ID IS NULL
                    THEN 1 ELSE 0 END), 0) AS UNASSIGNED,

                NVL(SUM(CASE
                    WHEN OFFICER_ID = ?
                    THEN 1 ELSE 0 END), 0) AS ASSIGNED_TO_ME

            FROM COOLING_OFF_REQUEST
            WHERE OFFICER_ID IS NULL
               OR OFFICER_ID = ?
            """;

        return jdbc.queryForObject(
            sql,
            (rs, rowNum) -> new DashboardSummary(

                rs.getLong("TOTAL_REQUESTS"),

                rs.getLong("PENDING"),

                rs.getLong("UNDER_REVIEW"),

                rs.getLong("APPROVED"),

                rs.getLong("COMPLETED"),

                rs.getLong("UNASSIGNED"),

                rs.getLong("ASSIGNED_TO_ME")
            ),
            officerId,
            officerId
        );
    }

    // =========================================
    // 4. ASSIGN REQUEST TO LOGGED-IN OFFICER
    // =========================================

    @Transactional
    public RequestDetails assignToMe(
        Long officerId,
        Long requestId
    ) {

        OfficerInfo officer = verifyOfficer(officerId);

        // Lock request to prevent simultaneous assignment
        String lockSql = """
            SELECT
                LOAN_ID,
                OFFICER_ID,
                STATUS
            FROM COOLING_OFF_REQUEST
            WHERE EXIT_REQUEST_ID = ?
            FOR UPDATE
            """;

        List<RequestState> requests = jdbc.query(
            lockSql,
            (rs, rowNum) -> new RequestState(
                rs.getLong("LOAN_ID"),
                nullableLong(rs, "OFFICER_ID"),
                rs.getString("STATUS")
            ),
            requestId
        );

        if (requests.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cooling-off request not found"
            );
        }

        RequestState request = requests.get(0);

        // Already assigned
        if (request.assignedOfficerId() != null) {

            if (request.assignedOfficerId()
                    .equals(officerId)) {

                return getRequestDetails(
                    officerId,
                    requestId
                );
            }

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request is assigned to another officer"
            );
        }

        // Only pending requests can be assigned
        if (!"PENDING".equals(request.status())) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only pending requests can be assigned"
            );
        }

        // Get the borrower who owns the loan
        Long borrowerId = jdbc.queryForObject(
            """
            SELECT BORROWER_ID
            FROM LOAN_ACCOUNT
            WHERE LOAN_ID = ?
            """,
            Long.class,
            request.loanId()
        );

        // Prevent known self-assignment
        if (officer.linkedUserId() != null
                && officer.linkedUserId().equals(
                    borrowerId
                )) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Cannot assign your own loan request"
            );
        }

        // Assign to current officer
        jdbc.update(
            """
            UPDATE COOLING_OFF_REQUEST
            SET OFFICER_ID = ?,
                STATUS = 'UNDER_REVIEW',
                UPDATED_AT = CURRENT_TIMESTAMP
            WHERE EXIT_REQUEST_ID = ?
            """,
            officerId,
            requestId
        );

        // Return updated request
        return getRequestDetails(
            officerId,
            requestId
        );
    }
}
