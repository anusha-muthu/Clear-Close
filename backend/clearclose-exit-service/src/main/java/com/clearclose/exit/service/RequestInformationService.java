package com.clearclose.exit.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.clearclose.exit.dto.ExitRequestDto.RequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestInformationService {
    private final JdbcTemplate jdbc;
    private final ExitRequestService requests;
    private final OfficerDashboardService officers;

    public RequestInformationService(JdbcTemplate jdbc, ExitRequestService requests, OfficerDashboardService officers) {
        this.jdbc = jdbc;
        this.requests = requests;
        this.officers = officers;
    }

    public record InformationMessage(long id, String actor, String message, LocalDateTime submittedAt) {}
    private record RequestState(String status, String loanStatus, boolean assigned) {}

    private void validateId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid request ID is required.");
    }

    @Transactional(readOnly = true)
    public List<InformationMessage> borrowerHistory(long borrowerId, Long requestId) {
        validateId(requestId);
        requests.getRequestById(borrowerId, requestId); // Existing ownership guard.
        return history(requestId);
    }

    @Transactional(readOnly = true)
    public List<InformationMessage> officerHistory(long officerId, Long requestId) {
        validateId(requestId);
        officers.getRequestDetails(officerId, requestId); // Existing active role + visibility checks.
        return history(requestId);
    }

    private List<InformationMessage> history(long requestId) {
        // Deliberate allowlist: never return payment/internal-service/general audit details.
        return jdbc.query("""
            SELECT LOG_ID, ACTION, DETAILS, ACTION_TIME FROM AUDIT_LOG
            WHERE EXIT_REQUEST_ID = ? AND ENTITY_ID = ? AND ENTITY_TYPE = 'COOLING_OFF_REQUEST'
              AND ACTION IN ('COOLING_OFF_MORE_INFO_REQUIRED', 'COOLING_OFF_INFO_SUBMITTED')
            ORDER BY ACTION_TIME, LOG_ID
            """, (rs, row) -> new InformationMessage(rs.getLong("LOG_ID"),
                "COOLING_OFF_INFO_SUBMITTED".equals(rs.getString("ACTION")) ? "BORROWER" : "OFFICER",
                rs.getString("DETAILS"), rs.getTimestamp("ACTION_TIME").toLocalDateTime()), requestId, requestId);
    }

    @Transactional
    public RequestResponse submit(long borrowerId, Long requestId, String response) {
        validateId(requestId);
        String message = response == null ? "" : response.trim();
        // Existing Oracle DETAILS is VARCHAR2(1000); stay safe for BYTE semantics too.
        if (message.isBlank() || message.length() > 1000 || message.getBytes(StandardCharsets.UTF_8).length > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Enter a nonblank response and shorten it to fit the message limit.");
        }
        var matches = jdbc.query("""
            SELECT c.STATUS, l.LOAN_STATUS, c.OFFICER_ID
            FROM COOLING_OFF_REQUEST c JOIN LOAN_ACCOUNT l ON l.LOAN_ID = c.LOAN_ID
            WHERE c.EXIT_REQUEST_ID = ? AND l.BORROWER_ID = ?
            FOR UPDATE OF c.STATUS
            """, (rs, row) -> new RequestState(rs.getString("STATUS"), rs.getString("LOAN_STATUS"),
                rs.getObject("OFFICER_ID") != null), requestId, borrowerId);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exit request not found for this borrower.");
        RequestState state = matches.getFirst();
        if (!"MORE_INFO_REQUIRED".equals(state.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This request is no longer awaiting information. Refresh its status before submitting.");
        }
        if (!"ACTIVE".equals(state.loanStatus()) || !state.assigned()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "An active loan and assigned officer are required for resubmission. Contact the bank.");
        }
        Integer settled = jdbc.queryForObject("""
            SELECT COUNT(*) FROM SETTLEMENT_QUOTE q WHERE q.EXIT_REQUEST_ID = ?
              AND (q.QUOTE_STATUS IN ('ACCEPTED', 'PAID') OR EXISTS (
                  SELECT 1 FROM PAYMENT p WHERE p.QUOTE_ID = q.QUOTE_ID AND p.PAYMENT_STATUS = 'SUCCESS'))
            """, Integer.class, requestId);
        if (settled != null && settled > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "This request has an accepted or paid settlement and requires bank review before resubmission.");
        int changed = jdbc.update("""
            UPDATE COOLING_OFF_REQUEST SET STATUS = 'UNDER_REVIEW', DECIDED_AT = NULL, UPDATED_AT = SYSTIMESTAMP
            WHERE EXIT_REQUEST_ID = ? AND STATUS = 'MORE_INFO_REQUIRED'
            """, requestId);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request changed; refresh and try again.");
        jdbc.update("""
            INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS, EXIT_REQUEST_ID)
            VALUES (?, ?, 'COOLING_OFF_REQUEST', 'COOLING_OFF_INFO_SUBMITTED', ?, ?)
            """, borrowerId, requestId, message, requestId);
        // Keep the original reason, officer, request date and deadline; no new request or financial writes.
        return requests.getRequestById(borrowerId, requestId);
    }
}
