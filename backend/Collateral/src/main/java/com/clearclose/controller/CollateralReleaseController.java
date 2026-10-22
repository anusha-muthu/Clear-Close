package com.clearclose.controller;

import com.clearclose.dto.OfficerDecisionRequest;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.service.CollateralReleaseService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collateral-release/requests")
@CrossOrigin(origins = "http://localhost:5173")
public class CollateralReleaseController {
    private final CollateralReleaseService releaseService;

    public CollateralReleaseController(CollateralReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @PostMapping("/submit")
    public CollateralReleaseRequests submitRequest(
            @RequestBody CollateralReleaseRequests request, @AuthenticationPrincipal Jwt jwt) {
        requireBorrower(jwt);
        return releaseService.submitRequest(request, userId(jwt), jwt.getTokenValue());
    }

    @GetMapping("/my-requests")
    public List<CollateralReleaseRequests> getMyRequests(@AuthenticationPrincipal Jwt jwt) {
        requireBorrower(jwt);
        return releaseService.getMyRequests(userId(jwt));
    }

    @GetMapping("/assigned")
    public List<CollateralReleaseRequests> getAssigned(@AuthenticationPrincipal Jwt jwt) {
        requireOfficer(jwt);
        return releaseService.getAssignedRequests(userId(jwt));
    }

    @PatchMapping("/{requestId}/assign")
    public CollateralReleaseRequests assign(
            @PathVariable("requestId") Long requestId,
            @RequestBody(required = false) Map<String, Long> body,
            @AuthenticationPrincipal Jwt jwt) {
        requireOfficer(jwt);
        if (!"ADMIN".equals(jwt.getClaimAsString("role"))) forbidden();
        return releaseService.assignRequest(requestId,
            body == null ? null : body.get("officerId"), userId(jwt));
    }

    @PatchMapping("/{requestId}/decision")
    public CollateralReleaseRequests decideRequest(
            @PathVariable("requestId") Long requestId,
            @RequestBody OfficerDecisionRequest body, @AuthenticationPrincipal Jwt jwt) {
        requireOfficer(jwt);
        return releaseService.decideRequest(requestId, userId(jwt), body.getDecision());
    }

    @PatchMapping("/{requestId}/complete-release")
    public CollateralReleaseRequests completeRelease(
            @PathVariable("requestId") Long requestId, @AuthenticationPrincipal Jwt jwt) {
        requireOfficer(jwt);
        return releaseService.completeRelease(requestId, userId(jwt));
    }

    @GetMapping
    public List<CollateralReleaseRequests> searchRequests(
            @RequestParam(name="loanId", required=false) Long loanId,
            @RequestParam(name="status", required=false) String status,
            @AuthenticationPrincipal Jwt jwt) {
        requireAccount(jwt);
        return releaseService.searchVisibleRequests(loanId, status, userId(jwt), jwt.getClaimAsString("accountType"));
    }

    @GetMapping("/{id}")
    public CollateralReleaseRequests getRequestById(
            @PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        requireAccount(jwt);
        return releaseService.getVisibleRequest(id, userId(jwt), jwt.getClaimAsString("accountType"));
    }

    @GetMapping("/loan/{loanId}")
    public List<CollateralReleaseRequests> getRequestsByLoanId(
            @PathVariable("loanId") Long loanId, @AuthenticationPrincipal Jwt jwt) {
        requireAccount(jwt);
        return releaseService.searchVisibleRequests(loanId, null, userId(jwt), jwt.getClaimAsString("accountType"));
    }

    @GetMapping("/user/{targetId}")
    public List<CollateralReleaseRequests> getRequestsByUserId(
            @PathVariable("targetId") Long targetId, @AuthenticationPrincipal Jwt jwt) {
        requireAccount(jwt);
        if ("BORROWER".equals(jwt.getClaimAsString("accountType")) && userId(jwt).equals(targetId)) {
            return releaseService.getMyRequests(targetId);
        }
        // The service validates ACTIVE admin state before returning all visible rows.
        if (!"ADMIN".equals(jwt.getClaimAsString("role"))) forbidden();
        return releaseService.searchVisibleRequests(null, null, userId(jwt), "OFFICER").stream()
            .filter(r -> targetId.equals(r.getRequestedBy())).toList();
    }

    @GetMapping("/status/{status}")
    public List<CollateralReleaseRequests> getRequestsByStatus(
            @PathVariable("status") String status, @AuthenticationPrincipal Jwt jwt) {
        requireAccount(jwt);
        return releaseService.searchVisibleRequests(null, status, userId(jwt), jwt.getClaimAsString("accountType"));
    }

    private static void forbidden() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account cannot access this operation");
    }

    private Long userId(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        try {
            Long value = Long.valueOf(String.valueOf((Object) jwt.getClaim("userId")));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token identity");
        }
    }

    private void requireBorrower(Jwt jwt) {
        userId(jwt);
        if (!"BORROWER".equals(jwt.getClaimAsString("accountType")) || !"BORROWER".equals(jwt.getClaimAsString("role"))) forbidden();
    }

    private void requireOfficer(Jwt jwt) {
        userId(jwt);
        if (!"OFFICER".equals(jwt.getClaimAsString("accountType"))) forbidden();
        String role = jwt.getClaimAsString("role");
        if (!"COLLATERAL_OFFICER".equals(role) && !"ADMIN".equals(role)) forbidden();
    }

    private void requireAccount(Jwt jwt) {
        userId(jwt);
        if ("BORROWER".equals(jwt.getClaimAsString("accountType"))) requireBorrower(jwt);
        else requireOfficer(jwt);
    }
}
