
package com.clearclose.exit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.clearclose.exit.dto.SettlementQuoteDto;
import com.clearclose.exit.service.QuoteAcceptanceService;

@RestController
@RequestMapping("/api/cooling-off/quotes")
public class QuoteAcceptanceController {

    private final QuoteAcceptanceService acceptanceService;

    public QuoteAcceptanceController(
        QuoteAcceptanceService acceptanceService
    ) {
        this.acceptanceService = acceptanceService;
    }

    // -----------------------------------------
    // ACCEPT SETTLEMENT QUOTE
    // -----------------------------------------

    @PostMapping("/{quoteId}/accept")
    public SettlementQuoteDto acceptQuote(
        @PathVariable Long quoteId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        // Borrower ID comes from verified JWT
        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return acceptanceService.acceptQuote(
            borrowerId,
            quoteId
        );
    }
}
