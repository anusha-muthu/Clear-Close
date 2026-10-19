package com.clearclose.controller;

import com.clearclose.dto.LoanAccountResponse;
import com.clearclose.entity.LoanAccount;
import com.clearclose.repository.LoanAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/loanAccounts")
public class LoanAccountController {

    private final LoanAccountRepository loanAccountRepository;

    public LoanAccountController(
            LoanAccountRepository loanAccountRepository) {
        this.loanAccountRepository = loanAccountRepository;
    }

    @GetMapping("/loan/{loanId}")
    public LoanAccountResponse getByLoanId(
            @PathVariable("loanId") Long loanId) {

        LoanAccount loan = loanAccountRepository
                .findByLoanId(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loan account not found"));

        return new LoanAccountResponse(
                loan.getLoanId(),
                loan.getBorrowerId(),
                loan.getRuleId(),
                loan.getLoanStatus(),
                loan.getOutstandingAmount()
        );
    }
}