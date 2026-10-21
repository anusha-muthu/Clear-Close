package com.clearclose.controller;

import com.clearclose.dto.OfficerDecisionRequest;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.service.CollateralReleaseService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collateral-release/requests")
@CrossOrigin(origins = "http://localhost:5173")
public class CollateralReleaseController {

    private final CollateralReleaseService releaseService;

    public CollateralReleaseController(
            CollateralReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @PostMapping("/submit")
    public CollateralReleaseRequests submitRequest(
            @RequestBody CollateralReleaseRequests request,
            @AuthenticationPrincipal Jwt jwt) {

        Long borrowerId = jwt.getClaim("userId");
        return releaseService.submitRequest(request, borrowerId, jwt.getTokenValue());
    }

    @GetMapping("/my-requests")
    public List<CollateralReleaseRequests> getMyRequests(
            @AuthenticationPrincipal Jwt jwt) {

        Long borrowerId = jwt.getClaim("userId");
        return releaseService.getMyRequests(borrowerId);
    }

    @PatchMapping("/{requestId}/decision")
    public CollateralReleaseRequests decideRequest(
            @PathVariable Long requestId,
            @RequestBody OfficerDecisionRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        Long officerId = jwt.getClaim("userId");
        return releaseService.decideRequest(
                requestId,
                officerId,
                body.getDecision());
    }

    @PatchMapping("/{requestId}/complete-release")
    public CollateralReleaseRequests completeRelease(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt) {

        Long officerId = jwt.getClaim("userId");
        return releaseService.completeRelease(requestId, officerId);
    }

    // Return all requests, or filter when query parameters are supplied.
    @GetMapping
    public List<CollateralReleaseRequests> searchRequests(
            @RequestParam(name = "loanId", required = false) Long loanId,
            @RequestParam(name = "status", required = false) String status) {

        if (loanId == null && status == null) {
            return releaseService.getAllRequests();
        }

        return releaseService.searchRequests(loanId, status);
    }

    @GetMapping("/{id}")
    public CollateralReleaseRequests getRequestById(
            @PathVariable("id") Long id) {
        return releaseService.getRequestById(id);
    }

    @GetMapping("/loan/{loanId}")
    public List<CollateralReleaseRequests> getRequestsByLoanId(
            @PathVariable("loanId") Long loanId) {
        return releaseService.getRequestsByLoanId(loanId);
    }

    @GetMapping("/user/{userId}")
    public List<CollateralReleaseRequests> getRequestsByUserId(
            @PathVariable("userId") Long userId) {
        return releaseService.getRequestsByUserId(userId);
    }

    @GetMapping("/status/{status}")
    public List<CollateralReleaseRequests> getRequestsByStatus(
            @PathVariable("status") String status) {
        return releaseService.getRequestsByStatus(status);
    }
}
