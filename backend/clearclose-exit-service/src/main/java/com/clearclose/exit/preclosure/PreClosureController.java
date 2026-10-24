package com.clearclose.exit.preclosure;

import com.clearclose.exit.completion.FinalExitCompletionService;
import com.clearclose.exit.preclosure.PreClosureService.PreClosureRequest;
import com.clearclose.exit.preclosure.PreClosureService.PreClosureQuote;
import com.clearclose.exit.preclosure.PreClosureService.PreClosureRecord;

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
    private final FinalExitCompletionService completion;

    public PreClosureController(
            PreClosureService service,
            PreClosurePaymentService payments, FinalExitCompletionService completion) {

        this.service = service;
        this.payments = payments;
        this.completion = completion;
    }

    private long userId(Jwt jwt) {
        return service.borrower(jwt);
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
            @PathVariable("requestId") long requestId) {

        return service.get(userId(jwt), requestId);
    }

    @GetMapping
    public List<PreClosureRecord> myRequests(@AuthenticationPrincipal Jwt jwt) {
        return service.myRequests(service.borrower(jwt));
    }

    @GetMapping("/{requestId}/information-history")
    public List<PreClosureService.InformationMessage> borrowerInformationHistory(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {
        return service.borrowerInformationHistory(service.borrower(jwt), requestId);
    }

    @PostMapping("/{requestId}/additional-information")
    public PreClosureRecord submitInformation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId, @RequestBody Map<String, String> body) {
        return service.submitInformation(service.borrower(jwt), requestId, body == null ? null : body.get("response"));
    }

    @GetMapping("/officer/{requestId}/information-history")
    public List<PreClosureService.InformationMessage> officerInformationHistory(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {
        return service.officerInformationHistory(service.officer(jwt), requestId);
    }

    @PostMapping("/officer/{requestId}/request-information")
    public PreClosureRecord requestInformation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId, @RequestBody Map<String, String> body) {
        return service.requestInformation(service.officer(jwt), requestId, body == null ? null : body.get("message"));
    }

    // -----------------------------------------
    // BORROWER: VIEW QUOTE
    // -----------------------------------------

    @GetMapping("/{requestId}/quote")
    public PreClosureQuote quote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {

        var quote = service.get(userId(jwt), requestId).quote();
        if (quote == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No quotation has been generated");
        return quote;
    }

    @PostMapping("/{requestId}/quote")
    public PreClosureQuote createQuote(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {
        return service.createQuote(service.borrower(jwt), requestId);
    }

    // -----------------------------------------
    // BORROWER: ACCEPT QUOTE
    // -----------------------------------------

    @PostMapping("/{requestId}/accept")
    public PreClosureRecord accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {

        return service.accept(userId(jwt), requestId);
    }

    // -----------------------------------------
    // BORROWER: CREATE RAZORPAY ORDER
    // -----------------------------------------

    @PostMapping("/{requestId}/payment/order")
    public PreClosurePaymentService.PaymentResult order(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {

        return payments.createOrder(
                userId(jwt), requestId);
    }

    // -----------------------------------------
    // BORROWER: VERIFY RAZORPAY PAYMENT
    // -----------------------------------------

    @PostMapping("/{requestId}/payment/verify")
    public PreClosurePaymentService.PaymentResult verify(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId,
            @RequestBody
            PreClosurePaymentService.VerifyRequest request) {

        long borrowerId = service.borrower(jwt);
        var result = payments.verify(borrowerId, requestId, request);
        completion.tryCompletePreClosure(requestId);
        return result;
    }

    // -----------------------------------------
    // BORROWER: PAYMENT STATUS
    // -----------------------------------------

    @GetMapping("/{requestId}/payment")
    public PreClosurePaymentService.PaymentResult paymentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {

        var result = payments.status(userId(jwt), requestId);
        if ("SUCCESS".equals(result.status())) completion.tryCompletePreClosure(requestId);
        return result;
    }

    // -----------------------------------------
    // OFFICER: PENDING REQUESTS
    // -----------------------------------------

    @GetMapping("/officer/requests")
    public List<Long> pendingRequests(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "includeHistory", defaultValue = "false") boolean includeHistory) {

        return service.pendingRequests(service.officer(jwt), includeHistory);
    }

    @GetMapping("/officer/{requestId}")
    public PreClosureRecord officerDetails(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {
        return service.officerDetails(service.officer(jwt), requestId);
    }

    @PostMapping("/officer/{requestId}/assign")
    public Map<String, String> assign(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {
        service.assign(service.officer(jwt), requestId);
        return Map.of("status", "UNDER_REVIEW");
    }

    // -----------------------------------------
    // OFFICER: APPROVE PRE-CLOSURE
    // -----------------------------------------

    @PostMapping("/officer/{requestId}/approve")
    public Map<String, String> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId) {

        service.approve(service.officer(jwt), requestId);

        return Map.of(
                "status", "APPROVED",
                "message", "Loan pre-closure approved");
    }

    // -----------------------------------------
    // OFFICER: REJECT PRE-CLOSURE
    // -----------------------------------------

    @PostMapping("/officer/{requestId}/reject")
    public Map<String, String> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("requestId") long requestId,
            @RequestBody Map<String, String> body) {

        service.reject(
                service.officer(jwt),
                requestId,
                body.get("reason"));

        return Map.of(
                "status", "REJECTED",
                "message", "Pre-closure request rejected");
    }
}
