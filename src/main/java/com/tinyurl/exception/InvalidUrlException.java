package com.tinyurl.exception;

import com.tinyurl.dto.ApiError;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {

        @ExceptionHandler({InvalidUrlException.class, MethodArgumentNotValidException.class})
        public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
            String message = ex instanceof MethodArgumentNotValidException validationException
                    ? validationException.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Invalid request")
                    : ex.getMessage();
            return build(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
        }

        @ExceptionHandler(UrlNotFoundException.class)
        public ResponseEntity<ApiError> handleNotFound(UrlNotFoundException ex, HttpServletRequest request) {
            return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
        }

        private ResponseEntity<ApiError> build(HttpStatus status, String message, String path) {
            ApiError error = new ApiError(
                    Instant.now(),
                    status.value(),
                    status.getReasonPhrase(),
                    message,
                    path);
            return ResponseEntity.status(status).body(error);
        }

    }
}
