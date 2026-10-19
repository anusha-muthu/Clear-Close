
package com.clearclose.exit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.clearclose.exit.dto.SettlementQuoteDto;
import com.clearclose.exit.service.SettlementQuoteService;

@RestController
@RequestMapping("/api/cooling-off")
public class SettlementQuoteController {

    private final SettlementQuoteService quoteService;

    public SettlementQuoteController(
        SettlementQuoteService quoteService
    ) {
        this.quoteService = quoteService;
    }

    // -----------------------------------------
    // CREATE OR REUSE SETTLEMENT QUOTE
    // -----------------------------------------

    @PostMapping("/requests/{requestId}/quote")
    public SettlementQuoteDto createQuote(
        @PathVariable Long requestId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return quoteService.createQuote(
            borrowerId,
            requestId
        );
    }

    // -----------------------------------------
    // GET QUOTE FOR EXIT REQUEST
    // -----------------------------------------

    @GetMapping("/requests/{requestId}/quote")
    public SettlementQuoteDto getRequestQuote(
        @PathVariable Long requestId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return quoteService.getQuoteForRequest(
            borrowerId,
            requestId
        );
    }

    // -----------------------------------------
    // GET QUOTE BY QUOTE ID
    // -----------------------------------------

    @GetMapping("/quotes/{quoteId}")
    public SettlementQuoteDto getQuoteById(
        @PathVariable Long quoteId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return quoteService.getQuoteById(
            borrowerId,
            quoteId
        );
    }
}
