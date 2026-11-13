package com.customer.payment.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.customer.payment.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handle_IdempotencyKey_Missing(MissingIdempotencyKeyException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.BAD_REQUEST, exception, request);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handle_IdempotencyKey_Invalid(InvalidIdempotencyKeyException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.BAD_REQUEST, exception, request);
    }
    
    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handle_IdempotencyKey_Conflict(IdempotencyKeyConflictException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.CONFLICT, exception, request);
    }

    @ExceptionHandler(PaymentAlreadyInProgressException.class)
    public ResponseEntity<ApiError> handle_Payment_InProgress(PaymentAlreadyInProgressException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.CONFLICT, exception, request);
    }

    @ExceptionHandler(PreviousPaymentAttemptFailedException.class)
    public ResponseEntity<ApiError> handle_Payment_Failed(PreviousPaymentAttemptFailedException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.CONFLICT, exception, request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handle_Payment_NotFound(PaymentNotFoundException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.NOT_FOUND, exception, request);
    }

    //ProviderUnavailableException -> 503

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSecurityIssues(AccessDeniedException exception, HttpServletRequest request) {
        return apiErrorHandler(HttpStatus.FORBIDDEN, exception, request);
    }
    
    private ResponseEntity<ApiError> apiErrorHandler(HttpStatus httpStatus, RuntimeException exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            httpStatus.value(),
            httpStatus.getReasonPhrase(),
            exception.getMessage(),
            request.getRequestURI());
        return ResponseEntity.status(httpStatus).body(error);

    }
}
    