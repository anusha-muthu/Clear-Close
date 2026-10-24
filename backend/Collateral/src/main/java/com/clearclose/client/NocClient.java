package com.clearclose.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NocClient {
    private final RestClient client;

    public NocClient(
            @Value("${noc.service.base-url:http://localhost:8088}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .baseUrl(baseUrl.trim().replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();
    }

    public boolean hasAvailableNoc(Long loanId, String bearerToken) {
        if (loanId == null || loanId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid loan ID is required");
        }
        String token = bearerToken == null ? "" : bearerToken.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            List<Map<String, Object>> certificates = client.get()
                    .uri("/api/noc/loan/{loanId}", loanId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (certificates == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NOC service returned an empty response");
            }
            // Validate the whole list before deciding. An unexpected DTO must not
            // silently look like a pending certificate.
            for (Map<String, Object> certificate : certificates) {
                if (certificate == null || !(certificate.get("status") instanceof String)) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NOC service returned an unexpected certificate response");
                }
            }
            // A failed reissue must not hide a previous certificate that is
            // still AVAILABLE. Successful reissue revokes the old certificate.
            return certificates.stream()
                    .anyMatch(certificate -> "AVAILABLE".equals(certificate.get("status")));
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "NOC service rejected the token. Please sign in again.", ex);
            }
            if (status == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOC service does not permit this account to view the loan's certificates", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to verify NOC issuance (NOC HTTP " + status + ")", ex);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "NOC service is unavailable or timed out. Try again later.", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NOC service returned an unreadable response", ex);
        }
    }
}
