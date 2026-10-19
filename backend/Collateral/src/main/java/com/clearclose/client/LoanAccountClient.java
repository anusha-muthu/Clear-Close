package com.clearclose.client;

import com.clearclose.dto.CollateralLoanResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class LoanAccountClient {

    private final RestClient loanAccountRestClient;

    public LoanAccountClient(
            @Qualifier("loanAccountRestClient") RestClient loanAccount) {
        this.loanAccountRestClient = loanAccount;
    }

    public CollateralLoanResponse getByLoanId(Long loanId) {
        try {
            return loanAccountRestClient.get()
                    .uri("/internal/loanAccounts/loan/{loanId}", loanId)
                    .retrieve()
                    .body(CollateralLoanResponse.class);
        } catch (RestClientException ex) {
            throw new RuntimeException(
                    "Unable to retrieve Loan id from Loan Service", ex);
        }
    }
}