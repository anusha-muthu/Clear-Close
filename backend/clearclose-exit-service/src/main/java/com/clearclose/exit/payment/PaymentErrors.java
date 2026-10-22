package com.clearclose.exit.payment;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Payment-specific errors; database internals and provider credentials stay on the server. */
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",
            ex.getReason() == null ? "Payment request failed." : ex.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(DataAccessException ex) {
        return ResponseEntity.status(503).body(Map.of("message",
            "Payment could not be saved. Refresh payment status before retrying; do not pay again if already charged."));
    }
}
