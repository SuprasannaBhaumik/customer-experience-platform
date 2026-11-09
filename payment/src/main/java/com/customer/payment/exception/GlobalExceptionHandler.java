package com.customer.payment.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.customer.payment.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentNotFound(PaymentNotFoundException exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            exception.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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

    @ExceptionHandler(PaymentAlreadyInProgressException.class)
    public ResponseEntity<ApiError> handlePaymentInProgress(PaymentAlreadyInProgressException exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
            exception.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(PreviousPaymentAttemptFailedException.class)
    public ResponseEntity<ApiError> handlePaymentFailed(PreviousPaymentAttemptFailedException exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            exception.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
    