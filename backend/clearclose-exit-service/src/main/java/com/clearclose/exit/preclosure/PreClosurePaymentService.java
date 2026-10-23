package com.clearclose.exit.preclosure;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.exit.payment.RazorpayClient;

@Service
public class PreClosurePaymentService {

    private final JdbcTemplate jdbc;
    private final RazorpayClient razorpay;

    public PreClosurePaymentService(
            JdbcTemplate jdbc,
            RazorpayClient razorpay) {
        this.jdbc = jdbc;
        this.razorpay = razorpay;
    }

    public record VerifyRequest(
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature
    ) {}

    public record PaymentResult(
        Long paymentId,
        Long quoteId,
        String razorpayOrderId,
        String razorpayPaymentId,
        BigDecimal amount,
        String status,
        String keyId
    ) {}

    private record QuoteInfo(
        long quoteId,
        long requestId,
        BigDecimal amount,
        String quoteStatus,
        String requestStatus,
        String loanStatus,
        boolean valid
    ) {}

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, message);
    }

    private QuoteInfo quote(long borrowerId, long requestId) {

        List<QuoteInfo> rows = jdbc.query("""
            SELECT q.QUOTE_ID, q.CLOSURE_REQUEST_ID,
                   q.TOTAL_AMOUNT, q.QUOTE_STATUS,
                   c.STATUS AS REQUEST_STATUS,
                   l.LOAN_STATUS,
                   CASE WHEN q.VALID_UNTIL > SYSDATE
                        THEN 1 ELSE 0 END AS VALID_QUOTE
            FROM SETTLEMENT_QUOTE q
            JOIN LOAN_CLOSURE_REQUEST c
              ON c.CLOSURE_REQUEST_ID = q.CLOSURE_REQUEST_ID
            JOIN LOAN_ACCOUNT l
              ON l.LOAN_ID = q.LOAN_ID
            WHERE c.CLOSURE_REQUEST_ID = ?
              AND c.CLOSURE_TYPE = 'FORECLOSURE'
              AND q.QUOTE_TYPE = 'CLOSURE'
              AND l.BORROWER_ID = ?
            ORDER BY q.QUOTE_ID DESC
            """,
            (rs, n) -> new QuoteInfo(
                rs.getLong("QUOTE_ID"),
                rs.getLong("CLOSURE_REQUEST_ID"),
                rs.getBigDecimal("TOTAL_AMOUNT"),
                rs.getString("QUOTE_STATUS"),
                rs.getString("REQUEST_STATUS"),
                rs.getString("LOAN_STATUS"),
                rs.getInt("VALID_QUOTE") == 1
            ),
            requestId, borrowerId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Pre-closure quotation not found");
        }

        return rows.get(0);
    }

    private long paise(BigDecimal amount) {
        try {
            long value = amount.movePointRight(2)
                    .longValueExact();

            if (value < 100) {
                throw conflict(
                        "Minimum payment is INR 1");
            }

            return value;
        } catch (ArithmeticException ex) {
            throw conflict("Invalid payment amount");
        }
    }

    private PaymentResult result(
            QuoteInfo q,
            Map<String, Object> payment) {

        return new PaymentResult(
            ((Number) payment.get("PAYMENT_ID")).longValue(),
            q.quoteId(),
            (String) payment.get("RAZORPAY_ORDER_ID"),
            (String) payment.get("RAZORPAY_PAYMENT_ID"),
            q.amount(),
            (String) payment.get("PAYMENT_STATUS"),
            razorpay.keyId()
        );
    }

   
 //CREATE RAZORPAY TEST ORDER

    @Transactional
    public PaymentResult createOrder(
            long borrowerId, long requestId) {

        QuoteInfo q = quote(borrowerId, requestId);

        if (!"ACTIVE".equals(q.loanStatus())
                || !"PAYMENT_PENDING".equals(q.requestStatus())
                || !"ACCEPTED".equals(q.quoteStatus())
                || !q.valid()) {
            throw conflict(
                    "Accepted, unexpired quote and active loan required");
        }

        if (!razorpay.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay Test Mode is not configured");
        }

        List<Map<String, Object>> existing =
                jdbc.queryForList("""
                    SELECT PAYMENT_ID, RAZORPAY_ORDER_ID,
                           RAZORPAY_PAYMENT_ID, PAYMENT_STATUS
                    FROM PAYMENT
                    WHERE QUOTE_ID = ?
                    ORDER BY PAYMENT_ID DESC
                    """, q.quoteId());

        if (!existing.isEmpty()) {
            Map<String, Object> payment = existing.get(0);

            if ("INITIATED".equals(
                    payment.get("PAYMENT_STATUS"))) {
                return result(q, payment);
            }

            if ("SUCCESS".equals(
                    payment.get("PAYMENT_STATUS"))) {
                throw conflict("Quote is already paid");
            }

            throw conflict(
                    "Existing payment requires review");
        }

        Map<String, Object> order =
                razorpay.createOrder(
                        paise(q.amount()), q.quoteId());

        String orderId = (String) order.get("id");

        jdbc.update("""
            INSERT INTO PAYMENT
                (QUOTE_ID, RAZORPAY_ORDER_ID,
                 AMOUNT, PAYMENT_STATUS)
            VALUES (?, ?, ?, 'INITIATED')
            """, q.quoteId(), orderId, q.amount());

        Map<String, Object> payment =
                jdbc.queryForMap("""
                    SELECT PAYMENT_ID, RAZORPAY_ORDER_ID,
                           RAZORPAY_PAYMENT_ID, PAYMENT_STATUS
                    FROM PAYMENT
                    WHERE QUOTE_ID = ?
                      AND RAZORPAY_ORDER_ID = ?
                    """, q.quoteId(), orderId);

        return result(q, payment);
    }

   
    // VERIFY RAZORPAY PAYMENT
    
    
    @Transactional
    public PaymentResult verify(
            long borrowerId,
            long requestId,
            VerifyRequest request) {

        if (request == null
                || request.razorpayOrderId() == null
                || request.razorpayPaymentId() == null
                || request.razorpaySignature() == null
                || !request.razorpayOrderId()
                    .matches("order_[A-Za-z0-9]+")
                || !request.razorpayPaymentId()
                    .matches("pay_[A-Za-z0-9]+")
                || !request.razorpaySignature()
                    .matches("[a-fA-F0-9]{64}")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid Razorpay payment details required");
        }

        QuoteInfo q = quote(borrowerId, requestId);

        Map<String, Object> payment;

        try {
            payment = jdbc.queryForMap("""
                SELECT PAYMENT_ID, RAZORPAY_ORDER_ID,
                       RAZORPAY_PAYMENT_ID, PAYMENT_STATUS
                FROM PAYMENT
                WHERE QUOTE_ID = ?
                  AND RAZORPAY_ORDER_ID = ?
                """,
                q.quoteId(), request.razorpayOrderId());

        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment order not found");
        }

        String storedStatus =
                (String) payment.get("PAYMENT_STATUS");

        if ("SUCCESS".equals(storedStatus)) {
            if (!request.razorpayPaymentId().equals(
                    payment.get("RAZORPAY_PAYMENT_ID"))) {
                throw conflict(
                        "Order was paid using a different payment");
            }
            return result(q, payment);
        }

        if (!"INITIATED".equals(storedStatus)) {
            throw conflict(
                    "Payment is not awaiting verification");
        }

        // Validate Razorpay checkout signature.
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                razorpay.keySecret().getBytes(
                        StandardCharsets.UTF_8),
                "HmacSHA256"
            ));

            String message =
                    request.razorpayOrderId() + "|"
                    + request.razorpayPaymentId();

            byte[] expected = mac.doFinal(
                    message.getBytes(StandardCharsets.UTF_8));

            byte[] supplied = HexFormat.of().parseHex(
                    request.razorpaySignature());

            if (!MessageDigest.isEqual(
                    expected, supplied)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid Razorpay signature");
            }

        } catch (java.security.GeneralSecurityException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payment verification unavailable");
        }

        Map<String, Object> provider =
                razorpay.payment(
                        request.razorpayPaymentId());

        if (!request.razorpayPaymentId().equals(
                    provider.get("id"))
                || !request.razorpayOrderId().equals(
                    provider.get("order_id"))
                || RazorpayClient.amount(provider)
                    != paise(q.amount())
                || !"INR".equals(provider.get("currency"))
                || !"captured".equals(provider.get("status"))) {

            throw conflict(
                    "Razorpay has not confirmed the expected captured payment");
        }

        String method = switch (
                String.valueOf(provider.get("method"))) {

            case "upi" -> "UPI";
            case "netbanking" -> "NET_BANKING";
            case "card" -> razorpay.cardMethod(
                    request.razorpayPaymentId());
            default -> null;
        };

        long paymentId =
                ((Number) payment.get("PAYMENT_ID")).longValue();

        int changed = jdbc.update("""
            UPDATE PAYMENT
            SET PAYMENT_STATUS = 'SUCCESS',
                RAZORPAY_PAYMENT_ID = ?,
                PAYMENT_DATE = SYSDATE,
                CONFIRMED_AT = SYSTIMESTAMP,
                METHOD = ?,
                UPDATED_AT = SYSTIMESTAMP
            WHERE PAYMENT_ID = ?
              AND PAYMENT_STATUS = 'INITIATED'
            """,
            request.razorpayPaymentId(),
            method,
            paymentId
        );

        if (changed != 1) {
            throw conflict(
                    "Payment state changed during verification");
        }

        jdbc.update("""
            UPDATE SETTLEMENT_QUOTE
            SET QUOTE_STATUS = 'PAID',
                UPDATED_AT = SYSTIMESTAMP
            WHERE QUOTE_ID = ?
              AND QUOTE_STATUS = 'ACCEPTED'
            """, q.quoteId());

        jdbc.update("""
            UPDATE LOAN_CLOSURE_REQUEST
            SET STATUS = 'UNDER_REVIEW',
                UPDATED_AT = SYSTIMESTAMP
            WHERE CLOSURE_REQUEST_ID = ?
              AND STATUS = 'PAYMENT_PENDING'
            """, requestId);

        jdbc.update("""
            INSERT INTO AUDIT_LOG
                (USER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, PAYMENT_ID, QUOTE_ID)
            VALUES (?, ?, 'PAYMENT',
                    'PRE_CLOSURE_PAYMENT_VERIFIED', ?, ?)
            """, borrowerId, paymentId,
                 paymentId, q.quoteId());

        Map<String, Object> updated =
                jdbc.queryForMap("""
                    SELECT PAYMENT_ID, RAZORPAY_ORDER_ID,
                           RAZORPAY_PAYMENT_ID, PAYMENT_STATUS
                    FROM PAYMENT
                    WHERE PAYMENT_ID = ?
                    """, paymentId);

        return result(q, updated);
    }

   
    // GET PAYMENT STATUS
   

    public PaymentResult status(
            long borrowerId, long requestId) {

        QuoteInfo q = quote(borrowerId, requestId);

        List<Map<String, Object>> payments =
                jdbc.queryForList("""
                    SELECT PAYMENT_ID, RAZORPAY_ORDER_ID,
                           RAZORPAY_PAYMENT_ID, PAYMENT_STATUS
                    FROM PAYMENT
                    WHERE QUOTE_ID = ?
                    ORDER BY PAYMENT_ID DESC
                    """, q.quoteId());

        if (payments.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment has not been initiated");
        }

        return result(q, payments.get(0));
    }
}