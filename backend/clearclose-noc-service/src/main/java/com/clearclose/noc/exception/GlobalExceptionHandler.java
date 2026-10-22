
package com.clearclose.noc.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(
            ResponseStatusException ex) {

        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", ex.getStatusCode().value(),
                "message", ex.getReason() == null
                    ? "Request failed" : ex.getReason()
            ));
    }
}
