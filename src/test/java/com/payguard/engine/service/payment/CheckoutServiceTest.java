package com.payguard.engine.service.payment;

import com.payguard.engine.dto.CheckoutRequest;
import com.payguard.engine.dto.CheckoutResponse;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.provider.dto.PaymentRequest;
import com.payguard.engine.provider.dto.PaymentResponse;
import com.payguard.engine.provider.impl.PayOsPaymentProvider;
import com.payguard.engine.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PayOsPaymentProvider paymentProvider;

    private ObjectMapper objectMapper;
    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        checkoutService = new CheckoutService(transactionRepository, paymentProvider, objectMapper);
    }

    @Test
    @DisplayName("Idempotency hit: returns cached response without calling provider")
    void testIdempotencyHit_ReturnsCachedResponse() throws Exception {
        String idempotencyKey = "idem-key-123";
        CheckoutResponse cachedResponse = new CheckoutResponse("TXN-1001", "https://checkout.payos.vn/1001", "qr-1001");
        String json = objectMapper.writeValueAsString(cachedResponse);

        Transaction existingTxn = Transaction.builder()
                .orderCode("TXN-1001")
                .payOsOrderCode(1001L)
                .amount(50000L)
                .status(TransactionStatus.PENDING)
                .appId("ASTRACINE")
                .idempotencyKey(idempotencyKey)
                .responseBody(json)
                .build();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTxn));

        CheckoutRequest request = new CheckoutRequest(50000L, "Test Payment", "https://return", "https://cancel");
        CheckoutResponse result = checkoutService.processCheckout(request, "ASTRACINE", idempotencyKey);

        assertEquals("TXN-1001", result.orderCode());
        assertEquals("https://checkout.payos.vn/1001", result.checkoutUrl());
        assertEquals("qr-1001", result.qrCodeString());

        verify(paymentProvider, never()).createPaymentLink(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Normal flow: pre-saves PENDING transaction, calls PayOS, saves response body")
    void testNormalFlow_Success() throws Exception {
        String idempotencyKey = "idem-key-new";
        long payOsOrderCode = 20260922123456L;

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentProvider.extractOrderCode(null)).thenReturn(payOsOrderCode);

        PaymentResponse mockProviderResponse = new PaymentResponse("PROVIDER-001", "https://checkout.payos.vn/url", "qr-code-data");
        when(paymentProvider.createPaymentLink(any(PaymentRequest.class))).thenReturn(mockProviderResponse);

        CheckoutRequest request = new CheckoutRequest(100000L, "Order 1", "https://return", "https://cancel");
        CheckoutResponse response = checkoutService.processCheckout(request, "ASTRACINE", idempotencyKey);

        assertNotNull(response);
        assertEquals("TXN-" + payOsOrderCode, response.orderCode());
        assertEquals("https://checkout.payos.vn/url", response.checkoutUrl());
        assertEquals("qr-code-data", response.qrCodeString());

        // Verify save was called at least twice (pre-save PENDING, and final update with responseBody)
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(2)).save(captor.capture());

        Transaction preSaved = captor.getAllValues().get(0);
        assertEquals("TXN-" + payOsOrderCode, preSaved.getOrderCode());
        assertEquals(payOsOrderCode, preSaved.getPayOsOrderCode());
        assertEquals(TransactionStatus.PENDING, preSaved.getStatus());

        Transaction finalizedTxn = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertNotNull(finalizedTxn.getResponseBody());
        assertTrue(finalizedTxn.getResponseBody().contains("https://checkout.payos.vn/url"));
    }

    @Test
    @DisplayName("Provider failure: updates transaction status to FAILED and throws exception")
    void testProviderFailure_MarksAsFailed() {
        String idempotencyKey = "idem-key-fail";
        long payOsOrderCode = 9999999L;

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentProvider.extractOrderCode(null)).thenReturn(payOsOrderCode);
        when(paymentProvider.createPaymentLink(any())).thenThrow(new RuntimeException("Gateway unavailable"));

        CheckoutRequest request = new CheckoutRequest(100000L, "Fail Order", "https://return", "https://cancel");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                checkoutService.processCheckout(request, "ASTRACINE", idempotencyKey)
        );

        assertTrue(thrown.getMessage().contains("Payment gateway error"));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(2)).save(captor.capture());

        Transaction lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(TransactionStatus.FAILED, lastSaved.getStatus());
    }

    @Test
    @DisplayName("Race condition on idempotency key: catches DataIntegrityViolationException and returns cached response")
    void testRaceCondition_ReturnsCachedResponse() throws Exception {
        String idempotencyKey = "idem-race";
        long payOsOrderCode = 8888888L;

        CheckoutResponse cachedResponse = new CheckoutResponse("TXN-8888888", "https://checkout.payos.vn/race", "qr-race");
        Transaction savedInParallel = Transaction.builder()
                .orderCode("TXN-8888888")
                .payOsOrderCode(payOsOrderCode)
                .amount(50000L)
                .status(TransactionStatus.PENDING)
                .appId("ASTRACINE")
                .idempotencyKey(idempotencyKey)
                .responseBody(objectMapper.writeValueAsString(cachedResponse))
                .build();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty()) // First check: not found yet
                .thenReturn(Optional.of(savedInParallel)); // Race fallback: found parallel record

        when(paymentProvider.extractOrderCode(null)).thenReturn(payOsOrderCode);
        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key idempotency_key"));

        CheckoutRequest request = new CheckoutRequest(50000L, "Race Order", "https://return", "https://cancel");
        CheckoutResponse response = checkoutService.processCheckout(request, "ASTRACINE", idempotencyKey);

        assertEquals("TXN-8888888", response.orderCode());
        assertEquals("https://checkout.payos.vn/race", response.checkoutUrl());
        verify(paymentProvider, never()).createPaymentLink(any());
    }
}
