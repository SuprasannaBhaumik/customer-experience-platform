package com.customer.favorites.exception;

import java.net.http.HttpRequest;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.customer.favorites.dto.ApiError;

@RestControllerAdvice 
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateFavoriteException.class)
    public ResponseEntity<ApiError> handleDuplicateException(HttpRequest request, DuplicateFavoriteException exception) {
        ApiError error = new ApiError(
            Instant.now(), 
            HttpStatus.INTERNAL_SERVER_ERROR.value(), 
            HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            exception.getMessage(),
            request.uri().getPath()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(NoProductFoundForCustomerException.class)
    public ResponseEntity<ApiError> handleNoProductFoundForCustomer(HttpRequest request, NoProductFoundForCustomerException exception) {
        ApiError error = new ApiError(
            Instant.now(), 
            HttpStatus.INTERNAL_SERVER_ERROR.value(), 
            HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            exception.getMessage(),
            request.uri().getPath()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

}
