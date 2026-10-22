package com.clearclose.exit.controller;

import java.util.List;
import com.clearclose.exit.dto.ExitRequestDto.RequestResponse;
import com.clearclose.exit.service.RequestInformationService;
import com.clearclose.exit.service.RequestInformationService.InformationMessage;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RequestInformationController {
    private final RequestInformationService information;
    public RequestInformationController(RequestInformationService information) { this.information = information; }
    public record AdditionalInformationRequest(String response) {}

    private long actorId(Jwt jwt, String accountType, String role) {
        if (jwt == null || !(jwt.getClaim("userId") instanceof Number id) || id.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid login is required.");
        }
        if (!accountType.equals(jwt.getClaimAsString("accountType")) || !role.equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account cannot access this operation.");
        }
        return ((Number) jwt.getClaim("userId")).longValue();
    }

    @GetMapping("/api/cooling-off/requests/{requestId}/information-history")
    public List<InformationMessage> borrowerHistory(@PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal Jwt jwt) {
        return information.borrowerHistory(actorId(jwt, "BORROWER", "BORROWER"), requestId);
    }

    @PostMapping("/api/cooling-off/requests/{requestId}/additional-information")
    public RequestResponse submit(@PathVariable("requestId") Long requestId,
            @RequestBody AdditionalInformationRequest body, @AuthenticationPrincipal Jwt jwt) {
        return information.submit(actorId(jwt, "BORROWER", "BORROWER"), requestId, body == null ? null : body.response());
    }

    @GetMapping("/api/officer/cooling-off/requests/{requestId}/information-history")
    public List<InformationMessage> officerHistory(@PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal Jwt jwt) {
        return information.officerHistory(actorId(jwt, "OFFICER", "LOAN_CLOSURE_OFFICER"), requestId);
    }
}
