package com.clearclose.noc.security;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NocAccess {
    public record Actor(long id, String accountType, String role) {}
    private record Officer(Long borrowerId, String role, String status) {}
    private final JdbcTemplate jdbc;

    public NocAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Actor actor(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required.");
        long id;
        try {
            id = Long.parseLong(String.valueOf((Object) jwt.getClaim("userId")));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token identity.");
        }
        String type = jwt.getClaimAsString("accountType");
        String role = jwt.getClaimAsString("role");
        if (!("BORROWER".equals(type) && "BORROWER".equals(role)
                || "OFFICER".equals(type) && List.of("LOAN_CLOSURE_OFFICER", "COLLATERAL_OFFICER", "ADMIN").contains(role == null ? "" : role))) {
            throw denied();
        }
        if (!(type + ":" + id).equals(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject does not match identity.");
        }
        return new Actor(id, type, role);
    }

    /** Called inside the service transaction, before returning data or writing files. */
    public void requireLoan(Actor actor, Long loanId, boolean write) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required.");
        if (loanId == null || loanId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid loan ID required.");
        if ("BORROWER".equals(actor.accountType()) && "BORROWER".equals(actor.role())) {
            if (write) throw denied();
            Integer owned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM LOAN_ACCOUNT l JOIN BORROWERS b ON b.USER_ID = l.BORROWER_ID
                WHERE l.LOAN_ID = ? AND b.USER_ID = ? AND b.ACCOUNT_STATUS = 'ACTIVE'
                """, Integer.class, loanId, actor.id());
            if (owned == null || owned != 1) throw notFound();
            return;
        }
        if (!"OFFICER".equals(actor.accountType())
                || !List.of("LOAN_CLOSURE_OFFICER", "COLLATERAL_OFFICER", "ADMIN").contains(actor.role() == null ? "" : actor.role())) throw denied();
        if (write && !List.of("LOAN_CLOSURE_OFFICER", "ADMIN").contains(actor.role())) throw denied();
        var officers = jdbc.query("SELECT USER_ID, ROLE, ACCOUNT_STATUS FROM OFFICER WHERE OFFICER_ID = ?",
            (rs, row) -> new Officer(rs.getObject("USER_ID") == null ? null : rs.getLong("USER_ID"),
                rs.getString("ROLE"), rs.getString("ACCOUNT_STATUS")), actor.id());
        if (officers.isEmpty() || !"ACTIVE".equals(officers.getFirst().status())
                || !actor.role().equals(officers.getFirst().role())) throw denied();
        var owners = jdbc.query("SELECT BORROWER_ID FROM LOAN_ACCOUNT WHERE LOAN_ID = ?",
            (rs, row) -> rs.getLong("BORROWER_ID"), loanId);
        if (owners.isEmpty()) throw notFound();
        if (write && owners.getFirst().equals(officers.getFirst().borrowerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot issue or manage an NOC for your own loan.");
        }
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is not authorized for this NOC operation.");
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan or certificate not found for this account.");
    }
}
