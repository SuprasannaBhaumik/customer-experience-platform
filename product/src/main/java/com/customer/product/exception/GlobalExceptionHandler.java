package com.customer.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.customer.product.dto.ApiError;

@RestControllerAdvice 
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException exception, HttpServletRequest request) {

        ApiError apiError = new ApiError(
            Instant.now(), 
            HttpStatus.NOT_FOUND.value(), 
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            exception.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSecurityIssues(AccessDeniedException exception, HttpServletRequest request) {

        ApiError error = new ApiError(
            Instant.now(), 
            HttpStatus.FORBIDDEN.value(), 
            HttpStatus.FORBIDDEN.getReasonPhrase(), 
            exception.getMessage(), 
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

}
