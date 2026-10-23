package com.clearclose.controller;

import com.clearclose.service.LoanClosureService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/loans")
public class InternalExitController {
    private final LoanClosureService closure;
    public InternalExitController(LoanClosureService closure) {
        this.closure = closure;
    }

    @PostMapping("/{loanId}/complete-pre-closure")
    public LoanClosureService.PreClosureResult preClose(@PathVariable("loanId") Long loanId,
            @RequestBody LoanClosureService.PreClosureRequest request) {
        return closure.closePreClosure(loanId, request);
    }

    @PostMapping("/{loanId}/complete-cooling-off-exit")
    public LoanClosureService.ClosureResult close(@PathVariable("loanId") Long loanId,
            @RequestBody LoanClosureService.ClosureRequest request) {
        return closure.close(loanId, request);
    }
}
