package com.payguard.engine.provider.impl;

import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.config.PayOsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayOsPaymentProviderTest {

    private RestClient restClient;
    private PayOsProperties properties;
    private ObjectMapper objectMapper;
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

        provider = new PayOsPaymentProvider(restClient, properties, objectMapper);
    }

    @Test
    @DisplayName("getProviderId should return PAYOS")
    void getProviderId() {
        assertEquals("PAYOS", provider.getProviderId());
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
}
