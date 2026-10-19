
package com.clearclose.loan.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.clearclose.loan.dto.LoanDto;
import com.clearclose.loan.service.LoanService;

@RestController
@RequestMapping("/api/borrower/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    // ---------------------------------------
    // GET ALL MY LOANS
    // ---------------------------------------

    @GetMapping("/list")
    public List<LoanDto> getMyLoans(
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId = jwt.getClaim("userId");

        return loanService.getMyLoans(borrowerId);
    }

    // ---------------------------------------
    // GET SPECIFIC LOAN
    // ---------------------------------------

    @GetMapping("/{loanId}")
    public LoanDto getLoanDetails(
        @PathVariable Long loanId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId = jwt.getClaim("userId");

        return loanService.getLoanById(
            borrowerId,
            loanId
        );
    }
}
