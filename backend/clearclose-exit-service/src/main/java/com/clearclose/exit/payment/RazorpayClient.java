package com.clearclose.exit.payment;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Uses Spring's existing HTTP client support; no extra backend SDK is needed. */
@Component
public class RazorpayClient {
    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);
    private final String keyId;
    private final String keySecret;
    private final RestClient client;

    public RazorpayClient(@Value("${razorpay.key-id}") String keyId,
                          @Value("${razorpay.key-secret}") String keySecret) {
        this.keyId = keyId.trim();
        this.keySecret = keySecret.trim();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = RestClient.builder().baseUrl("https://api.razorpay.com/v1")
            .requestFactory(factory)
            .defaultHeaders(headers -> headers.setBasicAuth(this.keyId, this.keySecret)).build();
    }

    public boolean configured() {
        return keyId.startsWith("rzp_test_") && !keyId.contains("REPLACE")
            && !keySecret.isBlank() && !keySecret.contains("REPLACE");
    }

    public String keyId() { requireTestKeys(); return keyId; }
    public String keySecret() { requireTestKeys(); return keySecret; }

    private void requireTestKeys() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Configure Razorpay Test Mode credentials on the backend.");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(long paise, long quoteId) {
        requireTestKeys();
        try {
            var result = client.post().uri("/orders")
                .body(Map.of("amount", paise, "currency", "INR", "partial_payment", false,
                    "receipt", "quote_" + quoteId,
                    "notes", Map.of("quoteId", String.valueOf(quoteId))))
                .retrieve().body(Map.class);
            if (result == null || !(result.get("id") instanceof String id)
                    || !id.startsWith("order_") || amount(result) != paise
                    || !"INR".equals(result.get("currency"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Razorpay order response.");
            }
            return result;
        } catch (RestClientException ex) {
            throw providerFailure("create-order", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> payment(String id) {
        requireTestKeys();
        if (id == null || !id.matches("pay_[A-Za-z0-9]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay payment ID.");
        }
        try {
            var result = client.get().uri("/payments/{id}", id).retrieve().body(Map.class);
            if (result == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty payment response.");
            return result;
        } catch (RestClientException ex) {
            throw providerFailure("verify-payment", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> orderPayments(String orderId) {
        requireTestKeys();
        try {
            var result = client.get().uri("/orders/{id}/payments", orderId).retrieve().body(Map.class);
            if (result == null || !(result.get("items") instanceof List<?>)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid payment status response.");
            }
            return (List<Map<String, Object>>) result.get("items");
        } catch (RestClientException ex) {
            throw providerFailure("refresh-payment", ex);
        }
    }

    private ResponseStatusException providerFailure(String operation, RestClientException ex) {
        // Log classifications only: provider bodies can contain customer data or credentials.
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            log.warn("Razorpay operation={} providerHttpStatus={}", operation, status);
            if (status == 401 || status == 403) {
                return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay rejected the backend credentials or account permissions. Check the Test Mode key pair in Exit Service.");
            }
            if (status == 400) {
                // Inspect only to classify; never forward an arbitrary provider description.
                String description = "";
                try {
                    Map<?, ?> body = response.getResponseBodyAs(Map.class);
                    if (body != null && body.get("error") instanceof Map<?, ?> error) {
                        description = String.valueOf(error.get("description")).toLowerCase(Locale.ROOT);
                    }
                } catch (RuntimeException ignored) {
                    // Fall back to a safe generic rejection message for non-JSON responses.
                }
                if (description.contains("amount") && (description.contains("maximum")
                        || description.contains("limit") || description.contains("exceed"))) {
                    log.warn("Razorpay operation={} rejection=AMOUNT_LIMIT", operation);
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Razorpay rejected the amount because of a payment limit. Check the account's permitted order amount; use a smaller test loan if necessary. The settlement amount has not been changed.");
                }
                return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay rejected the payment request (provider HTTP 400). Check the order amount and Razorpay account settings.");
            }
            if (status == 429) {
                return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is rate limiting requests. Wait before refreshing payment status.");
            }
        } else if (ex instanceof ResourceAccessException) {
            Throwable cause = ex;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            log.warn("Razorpay operation={} connectionFailure={}", operation, cause.getClass().getSimpleName());
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Exit Service could not connect securely to Razorpay or the request timed out. Check its internet, proxy and Java certificate settings. Refresh payment status before retrying.");
        } else {
            log.warn("Razorpay operation={} clientFailure={}", operation, ex.getClass().getSimpleName());
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Razorpay is unavailable or returned an unreadable response. Refresh payment status before retrying.");
    }

    public static long amount(Map<String, Object> data) {
        try { return new BigDecimal(String.valueOf(data.get("amount"))).longValueExact(); }
        catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Razorpay amount.");
        }
    }

    public String cardMethod(String paymentId) {
        requireTestKeys();
        try {
            var card = client.get().uri("/payments/{id}/card", paymentId).retrieve().body(Map.class);
            if (card == null) return null;
            return switch (String.valueOf(card.get("type"))) {
                case "credit" -> "CREDIT_CARD";
                case "debit" -> "DEBIT_CARD";
                default -> null;
            };
        } catch (RestClientException ex) {
            // Missing card metadata must not hide a securely confirmed captured payment.
            return null;
        }
    }
}
