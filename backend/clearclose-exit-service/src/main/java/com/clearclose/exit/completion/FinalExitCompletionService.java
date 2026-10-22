package com.clearclose.exit.completion;

import org.springframework.stereotype.Service;

/** Orchestration deliberately has no enclosing database transaction. */
@Service
public class FinalExitCompletionService {
    private final FinalExitStateService state;
    private final LoanAccountClosureClient loans;
    public FinalExitCompletionService(FinalExitStateService state, LoanAccountClosureClient loans) {
        this.state = state;
        this.loans = loans;
    }

    public boolean complete(long paymentId) {
        FinalExitStateService.Work work = state.load(paymentId);
        if ("COMPLETED".equals(work.requestStatus())) return state.finish(paymentId, null);
        // Even after a lost HTTP response this is safe: LoanAccount confirms the same
        // workflow's existing closure, and never treats another closure as our success.
        var closure = loans.close(work);
        return state.finish(paymentId, closure);
    }
}
