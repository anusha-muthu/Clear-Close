package com.ofss.exception;

import com.ofss.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, 
            HttpServletRequest request) {
        
        // Extract original status (e.g., 404) and reason text (e.g., "Grievance not found")
        int statusCode = ex.getStatusCode().value();
        String errorReason = ex.getStatusCode().toString(); 
        String customMessage = ex.getReason(); 

        ErrorResponse errorDetails = new ErrorResponse(
                statusCode,
                errorReason,
                customMessage,
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDetails, ex.getStatusCode());
    }
}
