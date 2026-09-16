package com.payguard.engine.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleStateMachineException(IllegalStateException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Invalid state transition", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequestException(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateRequest(DuplicateRequestException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message != null ? message : "");
        body.put("path", request != null ? request.getRequestURI() : "");
        return ResponseEntity.status(status).body(body);
    }
}