package com.clearclose.exit.preclosure;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pre-closures")
public class PreClosureController {

    private final PreClosureService service;
    private final PreClosurePaymentService payments;

    public PreClosureController(
            PreClosureService service,
            PreClosurePaymentService payments) {

        this.service = service;
        this.payments = payments;
    }

    private long userId(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        try {
            long id = Long.parseLong(jwt.getSubject());

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user ID");
        }
    }

    // -----------------------------------------
    // BORROWER: CREATE PRE-CLOSURE REQUEST
    // -----------------------------------------

    @PostMapping
    public PreClosureRecord create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PreClosureRequest request) {

        return service.create(userId(jwt), request);
    }

    // -----------------------------------------
    // BORROWER: TRACK REQUEST
    // -----------------------------------------

    @GetMapping("/{requestId}")
    public PreClosureRecord get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId) {

        return service.get(userId(jwt), requestId);
    }

    // -----------------------------------------
    // BORROWER: VIEW QUOTE
    // -----------------------------------------

    @GetMapping("/{requestId}/quote")
    public PreClosureQuote quote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId) {

        return service.get(
                userId(jwt), requestId).quote();
    }

    // -----------------------------------------
    // BORROWER: ACCEPT QUOTE
    // -----------------------------------------

    @PostMapping("/{requestId}/accept")
    public PreClosureRecord accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId) {

        return service.accept(userId(jwt), requestId);
    }

    // -----------------------------------------
    // BORROWER: CREATE RAZORPAY ORDER
    // -----------------------------------------

    @PostMapping("/{requestId}/payment/order")
    public PreClosurePaymentService.PaymentResult order(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId) {

        return payments.createOrder(
                userId(jwt), requestId);
    }

    // -----------------------------------------
    // BORROWER: VERIFY RAZORPAY PAYMENT
    // -----------------------------------------

    @PostMapping("/{requestId}/payment/verify")
    public PreClosurePaymentService.PaymentResult verify(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId,
            @RequestBody
            PreClosurePaymentService.VerifyRequest request) {

        return payments.verify(
                userId(jwt), requestId, request);
    }

    // -----------------------------------------
    // BORROWER: PAYMENT STATUS
    // -----------------------------------------

    @GetMapping("/{requestId}/payment")
    public PreClosurePaymentService.PaymentResult paymentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId) {

        return payments.status(
                userId(jwt), requestId);
    }

    // -----------------------------------------
    // OFFICER: PENDING REQUESTS
    // -----------------------------------------

    @GetMapping("/officer/requests")
    public List<Long> pendingRequests() {

        return service.pendingRequests();
    }

    // -----------------------------------------
    // OFFICER: APPROVE PRE-CLOSURE
    // -----------------------------------------

    @PostMapping("/officer/{requestId}/approve")
    public Map<String, String> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId) {

        service.approve(userId(jwt), requestId);

        return Map.of(
                "status", "COMPLETED",
                "message", "Loan pre-closure approved");
    }

    // -----------------------------------------
    // OFFICER: REJECT PRE-CLOSURE
    // -----------------------------------------

    @PostMapping("/officer/{requestId}/reject")
    public Map<String, String> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long requestId,
            @RequestBody Map<String, String> body) {

        service.reject(
                userId(jwt),
                requestId,
                body.get("reason"));

        return Map.of(
                "status", "REJECTED",
                "message", "Pre-closure request rejected");
    }
}