package com.clearclose.client;

import com.clearclose.dto.CollateralLoanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LoanAccountClient {

    private final RestClient loanAccountRestClient;

    public LoanAccountClient(
            @Qualifier("loanAccountRestClient") RestClient loanAccount) {
        this.loanAccountRestClient = loanAccount;
    }

    public CollateralLoanResponse getByLoanId(Long loanId, String bearerToken) {
        try {
            return loanAccountRestClient.get()
                    .uri("/internal/loanAccounts/loan/{loanId}", loanId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .body(CollateralLoanResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(
                        ex.getStatusCode(),
                        "Loan Account service rejected the token or loan access",
                        ex);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Loan Account service returned an error",
                    ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify loan details with Loan Account service",
                    ex);
        }
    }
}
