package com.payguard.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payos") // Ăn khớp chính xác với chữ payOS: trong file yml của bạn
public class PayOsProperties {

    private String baseUrl;
    private String clientId;
    private String apiKey;
    private String checksumKey;

    // --- BẮT BUỘC PHẢI CÓ GETTER VÀ SETTER ĐỂ SPRING BOOT ĐỔ DỮ LIỆU VÀO ---
    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChecksumKey() {
        return checksumKey;
    }

    public void setChecksumKey(String checksumKey) {
        this.checksumKey = checksumKey;
    }
}