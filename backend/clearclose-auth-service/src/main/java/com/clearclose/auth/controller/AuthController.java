
package com.clearclose.auth.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import com.clearclose.auth.dto.AuthDtos.AuthResponse;
import com.clearclose.auth.dto.AuthDtos.LoginRequest;
import com.clearclose.auth.dto.AuthDtos.SignupRequest;
import com.clearclose.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/borrower/signup")
    public ResponseEntity<AuthResponse> signup(
        @Valid @RequestBody SignupRequest request
    ) {
        try {
            

            return ResponseEntity.ok(
                authService.signupBorrower(request)
            );
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory
                .getLogger(AuthController.class)
                .error("Borrower signup failed", ex);
            throw ex;
        }
    }

    @PostMapping("/borrower/login")
    public ResponseEntity<AuthResponse> borrowerLogin(
        @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
            authService.loginBorrower(request)
        );
    }

    @PostMapping("/officer/login")
    public ResponseEntity<AuthResponse> officerLogin(
        @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
            authService.loginOfficer(request)
        );
    }

    // Protected endpoint to test JWT
    @GetMapping("/me")
    public Map<String, Object> currentUser(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return Map.of(
            "userId", jwt.getClaim("userId"),
            "username", jwt.getClaimAsString("username"),
            "accountType", jwt.getClaimAsString("accountType"),
            "role", jwt.getClaimAsString("role")
        );
    }
    
}
