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

        @Entity
        @Table(name = "url_mapping", uniqueConstraints = @UniqueConstraint(name = "uk_url_mapping_short_code", columnNames = "short_code"))
        public static class UrlMapping {

            @Id
            @GeneratedValue(strategy = GenerationType.IDENTITY)
            private Long id;

            @Column(name = "short_code", nullable = false, length = 12)
            private String shortCode;

            @Column(name = "original_url", nullable = false, length = 2048)
            private String originalUrl;

            @Column(name = "created_at", nullable = false)
            private Instant createdAt;

            @Column(name = "redirect_count", nullable = false)
            private long redirectCount;

            @Column(name = "last_accessed_at")
            private Instant lastAccessedAt;

            protected UrlMapping() {
            }

            public UrlMapping(String shortCode, String originalUrl, Instant createdAt) {
                this.shortCode = shortCode;
                this.originalUrl = originalUrl;
                this.createdAt = createdAt;
                this.redirectCount = 0;
            }

            public Long getId() {
                return id;
            }

            public String getShortCode() {
                return shortCode;
            }

            public String getOriginalUrl() {
                return originalUrl;
            }

            public Instant getCreatedAt() {
                return createdAt;
            }

            public long getRedirectCount() {
                return redirectCount;
            }

            public Instant getLastAccessedAt() {
                return lastAccessedAt;
            }

            public void recordRedirect(Instant accessedAt) {
                this.redirectCount++;
                this.lastAccessedAt = accessedAt;
            }
        }
    }
}
