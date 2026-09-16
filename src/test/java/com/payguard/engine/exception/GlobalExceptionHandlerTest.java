package com.payguard.engine.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/payments/checkout");
    }

    @Test
    void testHandleStateMachineException_Returns409Conflict() {
        IllegalStateException ex = new IllegalStateException("Invalid transition from SUCCESS with PAYMENT_SUCCESS");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleStateMachineException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Invalid state transition", response.getBody().get("error"));
        assertEquals("Invalid transition from SUCCESS with PAYMENT_SUCCESS", response.getBody().get("message"));
        assertEquals("/api/v1/payments/checkout", response.getBody().get("path"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void testHandleBadRequestException_Returns400BadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Missing mandatory Header X-Idempotency-Key!");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBadRequestException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Missing mandatory Header X-Idempotency-Key!", response.getBody().get("message"));
        assertEquals("/api/v1/payments/checkout", response.getBody().get("path"));
    }

    @Test
    void testHandleDuplicateRequest_Returns429TooManyRequests() {
        DuplicateRequestException ex = new DuplicateRequestException("Request is being processed, please do not click repeatedly!");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleDuplicateRequest(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().get("status"));
        assertEquals("Too Many Requests", response.getBody().get("error"));
        assertEquals("Request is being processed, please do not click repeatedly!", response.getBody().get("message"));
        assertEquals("/api/v1/payments/checkout", response.getBody().get("path"));
    }

    @Test
    void testHandleGeneralException_Returns500InternalServerError() {
        Exception ex = new RuntimeException("Unexpected database failure");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneralException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("Internal Server Error", response.getBody().get("error"));
        assertEquals("Unexpected database failure", response.getBody().get("message"));
    }

    @Test
    void testHandleException_WithNullMessage_DoesNotThrowNpe() {
        IllegalStateException ex = new IllegalStateException();
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleStateMachineException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().get("message"));
    }
}
