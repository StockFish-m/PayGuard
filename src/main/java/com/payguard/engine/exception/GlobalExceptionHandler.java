package com.payguard.engine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Hứng toàn bộ những ông RuntimeException do cái nhãn @Idempotent ném ra
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyException(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value()); // Mã 429: Gửi quá nhiều request trùng
        body.put("error", "Too Many Requests");
        body.put("message", ex.getMessage()); // Lấy đúng câu "Request dang duoc xu ly..." của bạn
        body.put("path", "/api/v1/payments/checkout");

        return new ResponseEntity<>(body, HttpStatus.TOO_MANY_REQUESTS);
    }
}