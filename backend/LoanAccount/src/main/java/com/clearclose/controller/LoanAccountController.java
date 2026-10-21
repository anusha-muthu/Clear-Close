package com.clearclose.controller;

import com.clearclose.dto.LoanAccountResponse;
import com.clearclose.entity.LoanAccount;
import com.clearclose.repository.LoanAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/internal/loanAccounts", "/api/borrower/loan-accounts"})
public class LoanAccountController {

    private final LoanAccountRepository loanAccountRepository;

    public LoanAccountController(
            LoanAccountRepository loanAccountRepository) {
        this.loanAccountRepository = loanAccountRepository;
    }

    @GetMapping("/loan/{loanId}")
    public LoanAccountResponse getByLoanId(
            @PathVariable("loanId") Long loanId,
            @AuthenticationPrincipal Jwt jwt) {

        LoanAccount loan = loanAccountRepository
                .findByLoanId(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loan account not found"));

        if ("BORROWER".equals(jwt.getClaimAsString("accountType"))) {
            Number authenticatedUserId = jwt.getClaim("userId");
            if (authenticatedUserId == null
                    || loan.getBorrowerId() == null
                    || !loan.getBorrowerId().equals(authenticatedUserId.longValue())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "This loan does not belong to the authenticated borrower");
            }
        }

        return new LoanAccountResponse(
                loan.getLoanId(),
                loan.getBorrowerId(),
                loan.getRuleId(),
                loan.getLoanStatus(),
                loan.getOutstandingAmount()
        );
    }
}
