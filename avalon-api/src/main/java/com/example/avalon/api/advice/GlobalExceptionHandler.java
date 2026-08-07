package com.example.avalon.api.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedOperation(UnsupportedOperationException exception) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiErrorResponse("NOT_IMPLEMENTED", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", exception.getMessage()));
    }

    public record ApiErrorResponse(String code, String message) {
    }
}
