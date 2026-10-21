package com.clearclose.exit.payment;

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

@Service
public class PaymentService {
    private final JdbcTemplate jdbc;
    private final RazorpayClient razorpay;

    public PaymentService(JdbcTemplate jdbc, RazorpayClient razorpay) {
        this.jdbc = jdbc;
        this.razorpay = razorpay;
    }

    private record Quote(long id, BigDecimal amount, String status, String requestStatus,
                         String loanStatus, boolean withinDeadline) {}
    private record Payment(long id, long quoteId, String orderId, String providerId,
                           BigDecimal amount, String status, LocalDateTime date, String method) {}
    public record PaymentView(Long paymentId, long quoteId, String razorpayOrderId,
                              String razorpayPaymentId, BigDecimal amount, long amountPaise,
                              String currency, String paymentStatus, LocalDateTime paymentDate,
                              String method, String keyId, boolean payable, String message) {}

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** All payment writers lock the same owned quote/request/loan rows before reading PAYMENT. */
    private Quote lockQuote(long borrowerId, Long quoteId) {
        if (quoteId == null || quoteId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid quote ID is required.");
        }
        // Match quote acceptance/generation: lock the exit request first.
        // Include ownership in the lock query so another borrower's rows are never locked.
        var requests = jdbc.queryForList("""
            SELECT c.EXIT_REQUEST_ID FROM COOLING_OFF_REQUEST c
            WHERE c.EXIT_REQUEST_ID = (
                SELECT q.EXIT_REQUEST_ID FROM SETTLEMENT_QUOTE q
                JOIN LOAN_ACCOUNT l ON l.LOAN_ID = q.LOAN_ID
                WHERE q.QUOTE_ID = ? AND q.QUOTE_TYPE = 'COOLING_OFF' AND l.BORROWER_ID = ?
            ) FOR UPDATE
            """, Long.class, quoteId, borrowerId);
        if (requests.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found for this borrower.");
        List<Quote> result = jdbc.query("""
            SELECT q.QUOTE_ID, q.TOTAL_AMOUNT, q.QUOTE_STATUS, c.STATUS AS REQUEST_STATUS,
                   l.LOAN_STATUS, CASE WHEN q.VALID_UNTIL > SYSDATE THEN 1 ELSE 0 END AS IN_TIME
            FROM SETTLEMENT_QUOTE q
            JOIN COOLING_OFF_REQUEST c ON c.EXIT_REQUEST_ID = q.EXIT_REQUEST_ID AND c.LOAN_ID = q.LOAN_ID
            JOIN LOAN_ACCOUNT l ON l.LOAN_ID = q.LOAN_ID
            WHERE q.QUOTE_ID = ? AND q.QUOTE_TYPE = 'COOLING_OFF' AND l.BORROWER_ID = ?
            FOR UPDATE OF c.STATUS, q.QUOTE_STATUS, l.LOAN_STATUS
            """, (rs, row) -> new Quote(rs.getLong("QUOTE_ID"), rs.getBigDecimal("TOTAL_AMOUNT"),
                rs.getString("QUOTE_STATUS"), rs.getString("REQUEST_STATUS"),
                rs.getString("LOAN_STATUS"), rs.getInt("IN_TIME") == 1), quoteId, borrowerId);
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found for this borrower.");
        return result.getFirst();
    }

    private Payment latest(long quoteId) {
        var rows = jdbc.query("""
            SELECT PAYMENT_ID, QUOTE_ID, RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID,
                   AMOUNT, PAYMENT_STATUS, PAYMENT_DATE, METHOD
            FROM PAYMENT WHERE QUOTE_ID = ?
            ORDER BY CASE WHEN PAYMENT_STATUS = 'SUCCESS' THEN 0 ELSE 1 END, PAYMENT_ID DESC
            """, (rs, row) -> {
                Timestamp date = rs.getTimestamp("PAYMENT_DATE");
                return new Payment(rs.getLong("PAYMENT_ID"), rs.getLong("QUOTE_ID"),
                    rs.getString("RAZORPAY_ORDER_ID"), rs.getString("RAZORPAY_PAYMENT_ID"),
                    rs.getBigDecimal("AMOUNT"), rs.getString("PAYMENT_STATUS"),
                    date == null ? null : date.toLocalDateTime(), rs.getString("METHOD"));
            }, quoteId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private long paise(BigDecimal rupees) {
        try {
            long amount = rupees.movePointRight(2).longValueExact();
            if (amount < 100) throw conflict("Minimum payment amount is INR 1.00.");
            return amount;
        } catch (ArithmeticException ex) {
            throw conflict("Invalid settlement amount precision or range.");
        }
    }

    private String blockedReason(Quote q) {
        if (!"ACCEPTED".equals(q.status())) return "Settlement quote must be ACCEPTED before payment.";
        if (!"APPROVED".equals(q.requestStatus())) return "Cooling-off request must be APPROVED before payment.";
        if (!"ACTIVE".equals(q.loanStatus())) return "Loan must remain ACTIVE before payment.";
        if (!q.withinDeadline()) return "Payment deadline has passed (VALID_UNTIL). No new checkout can be opened.";
        if (!razorpay.configured()) return "Razorpay Test Mode is not configured on the backend.";
        return null;
    }

    private PaymentView view(Quote q, Payment p, String override) {
        String message = p != null && "SUCCESS".equals(p.status()) ? "Payment verified successfully."
            : override != null ? override : blockedReason(q);
        boolean payable = message == null;
        if (p != null && "SUCCESS".equals(p.status())) payable = false;
        BigDecimal amount = p == null ? q.amount() : p.amount();
        return new PaymentView(p == null ? null : p.id(), q.id(), p == null ? null : p.orderId(),
            p == null ? null : p.providerId(), amount, paise(amount), "INR",
            p == null ? "NOT_STARTED" : p.status(), p == null ? null : p.date(),
            p == null ? null : p.method(), payable ? razorpay.keyId() : null, payable,
            message == null ? "Ready for a Razorpay test payment." : message);
    }

    private void audit(long borrowerId, Payment p, String action) {
        jdbc.update("""
            INSERT INTO AUDIT_LOG (USER_ID, ENTITY_ID, ENTITY_TYPE, ACTION, DETAILS, PAYMENT_ID, QUOTE_ID)
            VALUES (?, ?, 'PAYMENT', ?, 'Razorpay test-mode settlement payment', ?, ?)
            """, borrowerId, p.id(), action, p.id(), p.quoteId());
    }

    /** Provider data comes only from authenticated server-to-server Razorpay responses. */
    private void validateProvider(Payment p, Map<String, Object> data) {
        if (!p.orderId().equals(data.get("order_id")) || RazorpayClient.amount(data) != paise(p.amount())
                || !"INR".equals(data.get("currency"))) {
            throw conflict("Razorpay payment order, amount or currency does not match the stored payment.");
        }
    }

    private Payment capture(long borrowerId, Payment p, Map<String, Object> data) {
        validateProvider(p, data);
        if (!"captured".equals(data.get("status"))) return p;
        if (!(data.get("id") instanceof String id) || !id.matches("pay_[A-Za-z0-9]+")) {
            throw conflict("Invalid provider payment ID.");
        }
        String method = switch (String.valueOf(data.get("method"))) {
            case "upi" -> "UPI";
            case "netbanking" -> "NET_BANKING";
            case "card" -> razorpay.cardMethod(id);
            // Wallet and other methods have no matching CHECK value. Keep METHOD null;
            // never mislabel a captured payment as another method.
            default -> null;
        };
        int changed = jdbc.update("""
            UPDATE PAYMENT SET RAZORPAY_PAYMENT_ID = ?, PAYMENT_STATUS = 'SUCCESS',
                PAYMENT_DATE = SYSDATE, METHOD = ?, CONFIRMED_AT = SYSTIMESTAMP,
                UPDATED_AT = SYSTIMESTAMP
            WHERE PAYMENT_ID = ? AND PAYMENT_STATUS = 'INITIATED'
            """, id, method, p.id());
        if (changed != 1) throw conflict("Payment state changed; refresh payment status.");
        audit(borrowerId, p, "PAYMENT_VERIFIED");
        return latest(p.quoteId());
    }

    /** Reconcile interrupted callbacks; never create another order while funds are authorized. */
    private String reconcile(long borrowerId, Payment p) {
        if (p == null || "SUCCESS".equals(p.status())) return null;
        if (!"INITIATED".equals(p.status()) || p.orderId() == null) {
            return "Existing payment requires review; a new order will not be created automatically.";
        }
        boolean pending = false;
        for (var data : razorpay.orderPayments(p.orderId())) {
            validateProvider(p, data);
            if ("captured".equals(data.get("status"))) {
                capture(borrowerId, p, data);
                return null;
            }
            if ("authorized".equals(data.get("status")) || "created".equals(data.get("status"))) pending = true;
        }
        return pending ? "Payment is awaiting capture or confirmation. Refresh status; do not pay again." : null;
    }

    @Transactional
    public PaymentView status(long borrowerId, Long quoteId) {
        Quote q = lockQuote(borrowerId, quoteId);
        Payment p = latest(q.id());
        String pending = reconcile(borrowerId, p);
        return view(q, latest(q.id()), pending);
    }

    @Transactional
    public PaymentView order(long borrowerId, Long quoteId) {
        Quote q = lockQuote(borrowerId, quoteId);
        Payment p = latest(q.id());
        String pending = reconcile(borrowerId, p);
        p = latest(q.id());
        if (p != null && "SUCCESS".equals(p.status())) return view(q, p, null);
        // Re-evaluate the database clock after any provider round trip.
        q = lockQuote(borrowerId, quoteId);
        if (!razorpay.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure Razorpay Test Mode credentials on the backend.");
        }
        String blocked = blockedReason(q);
        if (blocked != null) throw conflict(blocked);
        if (pending != null) throw conflict(pending);
        if (p != null) {
            if (p.amount().compareTo(q.amount()) != 0) throw conflict("Stored order amount differs from quote.");
            return view(q, p, null);
        }
        var order = razorpay.createOrder(paise(q.amount()), q.id());
        String orderId = (String) order.get("id");
        jdbc.update("""
            INSERT INTO PAYMENT (QUOTE_ID, RAZORPAY_ORDER_ID, AMOUNT, PAYMENT_STATUS)
            VALUES (?, ?, ?, 'INITIATED')
            """, q.id(), orderId, q.amount());
        p = latest(q.id());
        audit(borrowerId, p, "PAYMENT_INITIATED");
        return view(lockQuote(borrowerId, quoteId), p, null);
    }

    @Transactional
    public PaymentView verify(long borrowerId, PaymentController.VerifyRequest request) {
        if (request.razorpayOrderId() == null || !request.razorpayOrderId().matches("order_[A-Za-z0-9]+")
                || request.razorpayPaymentId() == null || !request.razorpayPaymentId().matches("pay_[A-Za-z0-9]+")
                || request.razorpaySignature() == null || !request.razorpaySignature().matches("[a-fA-F0-9]{64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid order ID, payment ID and signature are required.");
        }
        var ids = jdbc.queryForList("SELECT QUOTE_ID FROM PAYMENT WHERE RAZORPAY_ORDER_ID = ?", Long.class,
            request.razorpayOrderId());
        if (ids.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found.");
        Quote q = lockQuote(borrowerId, ids.getFirst());
        Payment p = latest(q.id());
        if (p == null || !request.razorpayOrderId().equals(p.orderId())) throw conflict("Payment order is not current.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpay.keySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((p.orderId() + "|" + request.razorpayPaymentId()).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(request.razorpaySignature()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay payment signature.");
            }
        } catch (java.security.GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment verification unavailable.");
        }
        if ("SUCCESS".equals(p.status())) {
            if (!request.razorpayPaymentId().equals(p.providerId())) throw conflict("Quote already paid with a different payment.");
            return view(q, p, null);
        }
        if (!"INITIATED".equals(p.status())) throw conflict("Payment is not awaiting verification.");
        if (!"ACCEPTED".equals(q.status())) throw conflict("Quote is no longer accepted; payment requires review.");
        var data = razorpay.payment(request.razorpayPaymentId());
        if (!request.razorpayPaymentId().equals(data.get("id"))) throw conflict("Provider payment ID mismatch.");
        validateProvider(p, data);
        if (!"captured".equals(data.get("status"))) {
            return view(q, p, "Payment is not captured. Refresh status after capture; do not pay again.");
        }
        return view(q, capture(borrowerId, p, data), null);
    }
}
