package com.payguard.engine.provider.impl;

import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.config.PayOsProperties;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.util.PayGuardSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayOsPaymentProviderTest {

    private RestClient restClient;
    private PayOsProperties properties;
    private ObjectMapper objectMapper;
    private TransactionRepository transactionRepository;
    private PayGuardSecurityUtil securityUtil;
    private PayOsPaymentProvider provider;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        properties = new PayOsProperties();
        properties.setChecksumKey("test-checksum-key");
        properties.setBaseUrl("https://api-merchant.payos.vn");
        properties.setClientId("test-client-id");
        properties.setApiKey("test-api-key");
        objectMapper = new ObjectMapper();
        transactionRepository = mock(TransactionRepository.class);
        securityUtil = new PayGuardSecurityUtil();

        provider = new PayOsPaymentProvider(restClient, properties, objectMapper, transactionRepository, securityUtil);
    }

    @Test
    @DisplayName("getProviderId should return PAYOS")
    void getProviderId() {
        assertEquals("PAYOS", provider.getProviderId());
    }

    @Test
    @DisplayName("extractOrderCode should extract valid positive number from formatted transactionId")
    void extractOrderCode_withDigits() {
        long code = provider.extractOrderCode("TXN-00123");
        assertEquals(123L, code);
    }

    @Test
    @DisplayName("extractOrderCode should generate safe 53-bit positive orderCode for UUID string without digits")
    void extractOrderCode_withoutDigits_safeGenerator() {
        long code1 = provider.extractOrderCode("abcdef-xyz");
        long code2 = provider.extractOrderCode("abcdef-xyz");

        assertTrue(code1 > 0, "orderCode must be strictly positive");
        assertTrue(code1 <= 9007199254740991L, "orderCode must fit in 53-bit MAX_SAFE_INTEGER");
        assertTrue(code2 > 0, "orderCode must be strictly positive");
        assertNotEquals(code1, code2, "Subsequent fallback generation must be unique");
    }

    @Test
    @DisplayName("fetchReconciliationTransactions should return empty when no pending txns in DB")
    void fetchReconciliationTransactions_emptyDB() {
        when(transactionRepository.findByStatus(any())).thenReturn(Collections.emptyList());

        var list = provider.fetchReconciliationTransactions();
        assertNotNull(list);
        assertTrue(list.isEmpty());
        verify(transactionRepository, times(1)).findByStatus(any());
    }

    @Test
    @DisplayName("verifyWebhookSignature should return false when payload is missing data")
    void verifyWebhookSignature_missingData() {
        String invalidPayload = "{\"code\":\"00\"}";
        assertFalse(provider.verifyWebhookSignature(invalidPayload, "some-sig"));
    }

    @Test
    @DisplayName("verifyWebhookSignature should return false when signature does not match")
    void verifyWebhookSignature_signatureMismatch() {
        String payload = "{\"data\":{\"orderCode\":12345,\"amount\":10000},\"signature\":\"wrong-sig\"}";
        assertFalse(provider.verifyWebhookSignature(payload, "wrong-sig"));
    }

    @Test
    @DisplayName("verifyWebhookSignature should return true when signature matches using PayGuardSecurityUtil")
    void verifyWebhookSignature_signatureMatch() {
        String dataToHash = "amount=10000&orderCode=12345";
        String validSig = securityUtil.signHmacSha256(dataToHash, "test-checksum-key");

        String payload = "{\"data\":{\"orderCode\":12345,\"amount\":10000},\"signature\":\"" + validSig + "\"}";
        assertTrue(provider.verifyWebhookSignature(payload, validSig));
    }
}
