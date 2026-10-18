
package com.clearclose.auth.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.auth.dto.AuthDtos.AuthResponse;
import com.clearclose.auth.dto.AuthDtos.LoginRequest;
import com.clearclose.auth.dto.AuthDtos.SignupRequest;

@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
        JdbcTemplate jdbc,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    private record Account(
        Long id,
        String username,
        String passwordHash,
        String accountType,
        String role,
        String status
    ) {}

    // --------------------------------------------------
    // BORROWER SIGNUP
    // --------------------------------------------------

    @Transactional
    public AuthResponse signupBorrower(SignupRequest request) {

        String sql = """
            INSERT INTO BORROWERS
            (NAME, USERNAME, PASSWORD_HASH, EMAIL,
             PHONE, PAN_CARD, LOCATION)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try {
            jdbc.update(
                sql,
                request.name(),
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.phone(),
                request.panCard(),
                request.location()
            );

        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Username, email or PAN already exists, or data is invalid"
            );
        }

        Account account = findBorrower(request.username());

        return createResponse(account);
    }

    // --------------------------------------------------
    // BORROWER LOGIN
    // --------------------------------------------------

    public AuthResponse loginBorrower(LoginRequest request) {

        Account account = findBorrower(request.username());

        verifyCredentials(account, request.password());

        return createResponse(account);
    }

    // --------------------------------------------------
    // OFFICER LOGIN
    // --------------------------------------------------

    public AuthResponse loginOfficer(LoginRequest request) {

        Account account = findOfficer(request.username());

        verifyCredentials(account, request.password());

        return createResponse(account);
    }

    // --------------------------------------------------
    // FIND BORROWER
    // --------------------------------------------------

    private Account findBorrower(String username) {

        String sql = """
            SELECT USER_ID, USERNAME, PASSWORD_HASH,
                   ACCOUNT_STATUS
            FROM BORROWERS
            WHERE USERNAME = ?
            """;

        try {
            return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new Account(
                    rs.getLong("USER_ID"),
                    rs.getString("USERNAME"),
                    rs.getString("PASSWORD_HASH"),
                    "BORROWER",
                    "BORROWER",
                    rs.getString("ACCOUNT_STATUS")
                ),
                username
            );

        } catch (EmptyResultDataAccessException ex) {
            throw invalidLogin();
        }
    }

    // --------------------------------------------------
    // FIND OFFICER
    // --------------------------------------------------

    private Account findOfficer(String username) {

        String sql = """
            SELECT OFFICER_ID, USERNAME, PASSWORD_HASH,
                   ROLE, ACCOUNT_STATUS
            FROM OFFICER
            WHERE USERNAME = ?
            """;

        try {
            return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new Account(
                    rs.getLong("OFFICER_ID"),
                    rs.getString("USERNAME"),
                    rs.getString("PASSWORD_HASH"),
                    "OFFICER",
                    rs.getString("ROLE"),
                    rs.getString("ACCOUNT_STATUS")
                ),
                username
            );

        } catch (EmptyResultDataAccessException ex) {
            throw invalidLogin();
        }
    }

    // --------------------------------------------------
    // PASSWORD VERIFICATION
    // --------------------------------------------------

    private void verifyCredentials(
        Account account,
        String password
    ) {
        if (!passwordEncoder.matches(
            password,
            account.passwordHash()
        )) {
            throw invalidLogin();
        }

        if (!"ACTIVE".equals(account.status())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Account is not active"
            );
        }
    }

    private ResponseStatusException invalidLogin() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Invalid username or password"
        );
    }

    // --------------------------------------------------
    // JWT RESPONSE
    // --------------------------------------------------

    private AuthResponse createResponse(Account account) {

        String token = jwtService.generateToken(
            account.id(),
            account.username(),
            account.accountType(),
            account.role()
        );

        return new AuthResponse(
            token,
            "Bearer",
            account.accountType(),
            account.role(),
            account.id(),
            account.username()
        );
    }
}
