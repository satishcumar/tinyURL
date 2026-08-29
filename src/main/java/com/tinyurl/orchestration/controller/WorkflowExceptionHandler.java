package com.tinyurl.orchestration.controller;

import com.tinyurl.dto.ApiError;
import com.tinyurl.orchestration.exception.PolicyViolationException;
import com.tinyurl.orchestration.exception.WorkflowNotFoundException;
import com.tinyurl.orchestration.exception.WorkflowStateException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = WorkflowController.class)
public class WorkflowExceptionHandler {

    @ExceptionHandler(WorkflowNotFoundException.class)
    ResponseEntity<ApiError> notFound(WorkflowNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({WorkflowStateException.class, PolicyViolationException.class})
    ResponseEntity<ApiError> conflict(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid workflow request", request.getRequestURI());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path));
    }
}
