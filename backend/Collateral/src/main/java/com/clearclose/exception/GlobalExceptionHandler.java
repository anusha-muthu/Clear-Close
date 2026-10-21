package com.clearclose.exception;

import jakarta.servlet.http.HttpServletRequest;

import java.util.stream.Collectors;

import org.hibernate.PropertyValueException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, 
            HttpServletRequest request) {
        
        // Extract field validation failure messages and join them with commas
        String combinedErrorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        // Build your clean custom error payload
        ErrorResponse errorDetails = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                combinedErrorMessage, // Outputs: "collateralType: Collateral Type is required"
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }
    
    // Fallback handler for custom resource lookups (404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, 
            HttpServletRequest request) {
        
        ErrorResponse errorDetails = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String error = status == null
                ? "Request failed"
                : status.getReasonPhrase();
        String message = ex.getReason() == null ? error : ex.getReason();

        ErrorResponse errorDetails = new ErrorResponse(
                ex.getStatusCode().value(),
                error,
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(ex.getStatusCode()).body(errorDetails);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.hibernate.PropertyValueException.class)
    public ResponseEntity<ErrorResponse> handlePropertyValueException(
            org.hibernate.PropertyValueException ex, 
            HttpServletRequest request) {
        
        // Extract the exact property name that was missing (e.g., "collateralType")
        String missingField = ex.getPropertyName();

        ErrorResponse errorDetails = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                missingField + " is a required field.", // Clean message: "collateralType is a required field."
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }
    
    // Handle active/duplicate request validation issues
    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRequestException(
            DuplicateRequestException ex, 
            HttpServletRequest request) {
        
        ErrorResponse errorDetails = new ErrorResponse(
                HttpStatus.CONFLICT.value(),            // Status 409 represents a resource state conflict
                HttpStatus.CONFLICT.getReasonPhrase(),  // "Conflict"
                ex.getMessage(),                        // "An active release request already exists"
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }


}
