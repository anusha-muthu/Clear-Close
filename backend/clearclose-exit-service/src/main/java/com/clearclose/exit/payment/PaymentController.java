package com.clearclose.exit.payment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    public record OrderRequest(Long quoteId) {}
    public record VerifyRequest(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {}

    private long borrowerId(Jwt jwt) {
        if (jwt == null || !"BORROWER".equals(jwt.getClaimAsString("accountType"))
                || !"BORROWER".equals(jwt.getClaimAsString("role"))
                || !(jwt.getClaim("userId") instanceof Number id) || id.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid borrower token required.");
        }
        return ((Number) jwt.getClaim("userId")).longValue();
    }

    @PostMapping("/orders")
    public PaymentService.PaymentView order(@RequestBody OrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quote ID is required.");
        return service.order(borrowerId(jwt), request.quoteId());
    }

    @PostMapping("/verify")
    public PaymentService.PaymentView verify(@RequestBody VerifyRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification fields are required.");
        return service.verify(borrowerId(jwt), request);
    }

    // POST because refreshing can reconcile a captured payment into Oracle.
    @PostMapping("/quotes/{quoteId}/status")
    public PaymentService.PaymentView status(@PathVariable Long quoteId, @AuthenticationPrincipal Jwt jwt) {
        return service.status(borrowerId(jwt), quoteId);
    }
}
