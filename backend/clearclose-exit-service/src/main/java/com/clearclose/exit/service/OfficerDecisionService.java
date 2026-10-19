
package com.clearclose.exit.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.exit.dto.OfficerDecisionDto.DecisionRequest;
import com.clearclose.exit.dto.OfficerDecisionDto.DecisionResponse;



@Service
public class OfficerDecisionService {

    private final JdbcTemplate jdbc;

    public OfficerDecisionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -----------------------------------------
    // INTERNAL DATABASE RECORDS
    // -----------------------------------------

    private record OfficerInfo(
        Long officerId,
        Long linkedUserId,
        String panCard,
        String role,
        String accountStatus
    ) {}

    private record RequestInfo(
        Long loanId,
        Long borrowerId,
        Long assignedOfficerId,
        String status,
        String loanStatus,
        String borrowerPan,
        LocalDateTime requestDate,
        LocalDateTime expiryDate
    ) {}

    // -----------------------------------------
    // HELPER: NULLABLE NUMBER
    // -----------------------------------------

    private Long nullableLong(
        java.sql.ResultSet rs,
        String column
    ) throws java.sql.SQLException {

        Number number = (Number) rs.getObject(column);

        return number == null
            ? null
            : number.longValue();
    }

    // =========================================
    // OFFICER REVIEW AND DECISION
    // =========================================

    @Transactional
    public DecisionResponse makeDecision(
        Long officerId,
        Long requestId,
        DecisionRequest request
    ) {

        // 1. Validate input

        if (request == null ||
            request.decision() == null) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Decision is required"
            );
        }

        String decision = request.decision()
            .trim()
            .toUpperCase();

        if (!List.of(
            "APPROVED",
            "REJECTED",
            "MORE_INFO_REQUIRED"
        ).contains(decision)) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid officer decision"
            );
        }

        String remarks = request.remarks() == null
            ? ""
            : request.remarks().trim();

        if (remarks.isBlank() || remarks.length() > 1000) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Remarks must contain 1 to 1000 characters"
            );
        }

        // 2. Verify logged-in officer

        String officerSql = """
            SELECT
                OFFICER_ID,
                USER_ID,
                PAN_CARD,
                ROLE,
                ACCOUNT_STATUS
            FROM OFFICER
            WHERE OFFICER_ID = ?
            """;

        List<OfficerInfo> officers = jdbc.query(
            officerSql,
            (rs, rowNum) -> new OfficerInfo(
                rs.getLong("OFFICER_ID"),
                nullableLong(rs, "USER_ID"),
                rs.getString("PAN_CARD"),
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
                "Officer is not authorized"
            );
        }

        // 3. Lock and retrieve exit request

        String requestSql = """
            SELECT
                c.LOAN_ID,
                c.OFFICER_ID,
                c.STATUS,
                c.REQUEST_DATE,
                c.EXPIRY_DATE,
                l.BORROWER_ID,
                l.LOAN_STATUS,
                b.PAN_CARD AS BORROWER_PAN

            FROM COOLING_OFF_REQUEST c

            JOIN LOAN_ACCOUNT l
                ON c.LOAN_ID = l.LOAN_ID

            JOIN BORROWERS b
                ON l.BORROWER_ID = b.USER_ID

            WHERE c.EXIT_REQUEST_ID = ?

            FOR UPDATE OF c.STATUS
            """;

        List<RequestInfo> requests = jdbc.query(
            requestSql,
            (rs, rowNum) -> {

                Timestamp requestDate =
                    rs.getTimestamp("REQUEST_DATE");

                Timestamp expiryDate =
                    rs.getTimestamp("EXPIRY_DATE");

                return new RequestInfo(
                    rs.getLong("LOAN_ID"),
                    rs.getLong("BORROWER_ID"),
                    nullableLong(rs, "OFFICER_ID"),
                    rs.getString("STATUS"),
                    rs.getString("LOAN_STATUS"),
                    rs.getString("BORROWER_PAN"),
                    requestDate == null
                        ? null
                        : requestDate.toLocalDateTime(),
                    expiryDate == null
                        ? null
                        : expiryDate.toLocalDateTime()
                );
            },
            requestId
        );

        if (requests.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Exit request not found"
            );
        }

        RequestInfo exitRequest = requests.get(0);

        // 4. Request must be assigned to this officer

        if (exitRequest.assignedOfficerId() == null ||
            !exitRequest.assignedOfficerId()
                .equals(officerId)) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Request is not assigned to you"
            );
        }

        // 5. Only UNDER_REVIEW requests can be decided

        if (!"UNDER_REVIEW".equals(
            exitRequest.status()
        )) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request must be UNDER_REVIEW"
            );
        }

        // 6. Loan must still be active

        if (!"ACTIVE".equals(
            exitRequest.loanStatus()
        )) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Loan is no longer active"
            );
        }

        // 7. Validate original request timing
        // The officer can review after expiry, provided
        // the borrower submitted within the deadline.

        if (exitRequest.requestDate() == null ||
            exitRequest.expiryDate() == null ||
            exitRequest.requestDate().isAfter(
                exitRequest.expiryDate()
            )) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Original cooling-off request dates are invalid"
            );
        }

        // 8. Prevent self-review
        // Compare linked borrower account and PAN.
        // If identity cannot be checked, block decision.

        boolean linkedIdentityAvailable =
            officer.linkedUserId() != null;

        boolean panIdentityAvailable =
            officer.panCard() != null &&
            !officer.panCard().isBlank() &&
            exitRequest.borrowerPan() != null &&
            !exitRequest.borrowerPan().isBlank();

        if (!linkedIdentityAvailable &&
            !panIdentityAvailable) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Officer identity verification is required"
            );
        }

        if (linkedIdentityAvailable &&
            officer.linkedUserId().equals(
                exitRequest.borrowerId()
            )) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot review your own loan request"
            );
        }

        if (panIdentityAvailable &&
            officer.panCard().equalsIgnoreCase(
                exitRequest.borrowerPan()
            )) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot review your own loan request"
            );
        }

        // 9. Update request status and decision time

        jdbc.update(
            """
            UPDATE COOLING_OFF_REQUEST
            SET STATUS = ?,
                DECIDED_AT = SYSTIMESTAMP,
                UPDATED_AT = SYSTIMESTAMP
            WHERE EXIT_REQUEST_ID = ?
            """,
            decision,
            requestId
        );

        // 10. Record officer action in AUDIT_LOG

        String action = switch (decision) {
            case "APPROVED" ->
                "COOLING_OFF_APPROVED";

            case "REJECTED" ->
                "COOLING_OFF_REJECTED";

            default ->
                "COOLING_OFF_MORE_INFO_REQUIRED";
        };

        jdbc.update(
            """
            INSERT INTO AUDIT_LOG
            (
                OFFICER_ID,
                ENTITY_ID,
                ENTITY_TYPE,
                ACTION,
                DETAILS,
                EXIT_REQUEST_ID
            )
            VALUES (
                ?, ?, 'COOLING_OFF_REQUEST',
                ?, ?, ?
            )
            """,
            officerId,
            requestId,
            action,
            remarks,
            requestId
        );

        // 11. Retrieve updated decision timestamp

        LocalDateTime decidedAt = jdbc.queryForObject(
            """
            SELECT DECIDED_AT
            FROM COOLING_OFF_REQUEST
            WHERE EXIT_REQUEST_ID = ?
            """,
            (rs, rowNum) ->
                rs.getTimestamp("DECIDED_AT")
                    .toLocalDateTime(),
            requestId
        );

        // 12. Return response

        return new DecisionResponse(
            requestId,
            exitRequest.loanId(),
            officerId,
            decision,
            remarks,
            decidedAt
        );
    }
}
