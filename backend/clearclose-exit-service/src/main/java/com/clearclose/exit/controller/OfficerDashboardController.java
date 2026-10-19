
package com.clearclose.exit.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.clearclose.exit.dto.OfficerDashboardDto.DashboardSummary;
import com.clearclose.exit.dto.OfficerDashboardDto.RequestDetails;
import com.clearclose.exit.service.OfficerDashboardService;


@RestController
@RequestMapping("/api/officer/cooling-off")
public class OfficerDashboardController {

    private final OfficerDashboardService officerService;

    public OfficerDashboardController(
        OfficerDashboardService officerService
    ) {
        this.officerService = officerService;
    }

    // -----------------------------------------
    // GET ALL VISIBLE REQUESTS
    // -----------------------------------------

    @GetMapping("/requests")
    public List<RequestDetails> getAllRequests(
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long officerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return officerService.getAllRequests(officerId);
    }

    // -----------------------------------------
    // GET REQUEST DETAILS
    // -----------------------------------------

    @GetMapping("/requests/{requestId}")
    public RequestDetails getRequestDetails(
        @PathVariable Long requestId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long officerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return officerService.getRequestDetails(
            officerId,
            requestId
        );
    }

    // -----------------------------------------
    // GET DASHBOARD SUMMARY
    // -----------------------------------------

    @GetMapping("/summary")
    public DashboardSummary getSummary(
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long officerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return officerService.getSummary(officerId);
    }

    // -----------------------------------------
    // ASSIGN REQUEST TO CURRENT OFFICER
    // -----------------------------------------

    @PatchMapping("/requests/{requestId}/assign")
    public RequestDetails assignRequest(
        @PathVariable Long requestId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long officerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return officerService.assignToMe(
            officerId,
            requestId
        );
    }
}
