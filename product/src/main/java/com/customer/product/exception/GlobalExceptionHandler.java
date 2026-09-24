package com.customer.product.exception;

import java.net.http.HttpRequest;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.customer.product.dto.ApiError;

@RestControllerAdvice 
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException exception, HttpRequest request) {

        ApiError apiError = new ApiError(
            Instant.now(), 
            HttpStatus.INTERNAL_SERVER_ERROR.value(), 
            HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            exception.getMessage(),
            request.uri().getPath()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

}
