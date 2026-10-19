
package com.clearclose.exit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.clearclose.exit.dto.OfficerDecisionDto.DecisionRequest;
import com.clearclose.exit.dto.OfficerDecisionDto.DecisionResponse;
import com.clearclose.exit.service.OfficerDecisionService;



@RestController
@RequestMapping("/api/officer/cooling-off")
public class OfficerDecisionController {

    private final OfficerDecisionService decisionService;

    public OfficerDecisionController(
        OfficerDecisionService decisionService
    ) {
        this.decisionService = decisionService;
    }

    // -----------------------------------------
    // OFFICER DECISION API
    // -----------------------------------------

    @PatchMapping("/requests/{requestId}/decision")
    public DecisionResponse makeDecision(
        @PathVariable Long requestId,
        @RequestBody DecisionRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long officerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return decisionService.makeDecision(
            officerId,
            requestId,
            request
        );
    }
}
