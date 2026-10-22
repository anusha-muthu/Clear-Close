package com.clearclose.controller;

import com.clearclose.service.CoolingOffLoanClosureService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/loans")
public class InternalExitController {
    private final CoolingOffLoanClosureService closure;
    public InternalExitController(CoolingOffLoanClosureService closure) { this.closure = closure; }

    @PostMapping("/{loanId}/complete-cooling-off-exit")
    public CoolingOffLoanClosureService.ClosureResult close(@PathVariable("loanId") Long loanId,
            @RequestBody CoolingOffLoanClosureService.ClosureRequest request) {
        return closure.close(loanId, request);
    }
}
