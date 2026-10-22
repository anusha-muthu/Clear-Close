package com.clearclose.exit.payment;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Uses Spring's existing HTTP client support; no extra backend SDK is needed. */
@Component
public class RazorpayClient {
    private final String keyId;
    private final String keySecret;
    private final RestClient client;

    public RazorpayClient(@Value("${razorpay.key-id}") String keyId,
                          @Value("${razorpay.key-secret}") String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = RestClient.builder().baseUrl("https://api.razorpay.com/v1")
            .requestFactory(factory)
            .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret)).build();
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
            // Never include provider bodies, Basic Auth headers or credentials in errors/logs.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Razorpay order creation failed. Please retry later.");
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to confirm payment with Razorpay.");
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to refresh Razorpay payment status.");
        }
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
