package com.clearclose.exit.completion;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LoanAccountClosureClient {
    private final RestClient client;
    private final String secret;
    public record ClosureResult(Long loanId, Long paymentId, Long quoteId, Long exitRequestId,
            String loanStatus, BigDecimal outstandingAmount, LocalDateTime closureDate) {}

    public LoanAccountClosureClient(@Value("${clearclose.loan-account.base-url}") String baseUrl,
            @Value("${clearclose.internal.exit-secret}") String secret) {
        this.secret = secret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private String serviceToken() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() || secret.contains("REPLACE") || bytes.length < 32) {
            throw new IllegalStateException("Configure the same EXIT_LOAN_SERVICE_SECRET (32+ bytes) in both services.");
        }
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(new SecretKeySpec(bytes, "HmacSHA256")));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("clearclose-exit-service")
            .subject("clearclose-exit-service").audience(List.of("clearclose-loan-account-internal"))
            .issuedAt(now).expiresAt(now.plusSeconds(120)).claim("serviceRole", "EXIT_SERVICE").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public ClosureResult close(FinalExitStateService.Work work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Do not hold an Exit Service transaction during a remote loan closure.");
        }
        try {
            ClosureResult result = client.post()
                .uri("/api/internal/loans/{loanId}/complete-cooling-off-exit", work.loanId())
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .body(Map.of("paymentId", work.paymentId(), "quoteId", work.quoteId(), "exitRequestId", work.exitRequestId()))
                .retrieve().body(ClosureResult.class);
            if (result == null || !Long.valueOf(work.loanId()).equals(result.loanId())
                    || !Long.valueOf(work.paymentId()).equals(result.paymentId())
                    || !Long.valueOf(work.quoteId()).equals(result.quoteId())
                    || !Long.valueOf(work.exitRequestId()).equals(result.exitRequestId())
                    || !"CLOSED".equals(result.loanStatus()) || result.outstandingAmount() == null
                    || result.outstandingAmount().signum() != 0 || result.closureDate() == null) {
                throw new IllegalStateException("LoanAccount did not confirm the requested full closure.");
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("LoanAccount closure rejected: HTTP " + ex.getStatusCode().value()
                + ". Check service authentication and workflow state; the work remains pending.");
        } catch (RestClientException ex) {
            throw new IllegalStateException("LoanAccount closure is unavailable. The same workflow will be retried.");
        }
    }
}
