
package com.clearclose.exit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clearclose.exit.dto.EligibilityDto;
import com.clearclose.exit.service.EligibilityService;

@RestController
@RequestMapping("/api/cooling-off")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    public EligibilityController(
        EligibilityService eligibilityService
    ) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/eligibility/{loanId}")
    public EligibilityDto checkEligibility(
        @PathVariable Long loanId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        // Get borrower ID from verified JWT
        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        // Call business logic
        return eligibilityService.checkEligibility(
            loanId,
            borrowerId
        );
    }
}
