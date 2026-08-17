package com.payguard.engine.service.payment;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.config.PayOsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PayOsSecurityService {

    private static final Logger log = LoggerFactory.getLogger(PayOsSecurityService.class);
    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;

    public PayOsSecurityService(PayOsProperties payOsProperties, ObjectMapper objectMapper) {
        this.payOsProperties = payOsProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Hàm xử lý trọn gói: Tự động trích xuất JSON, sắp xếp A-Z, và kiểm tra Chữ
     * ký
     */
    public boolean verifyWebhookSignature(JsonNode dataNode, String signatureFromPayOs) {
        try {
            String checksumKey = payOsProperties.getChecksumKey();
            if (checksumKey == null || checksumKey.isEmpty()) {
                log.error("==> [Security] ERROR: PAYOS_CHECKSUM_KEY is not configured in properties");
                return false;
            }

            // --- BƯỚC 1: SẮP XẾP VÀ TẠO CHUỖI CHUẨN PAYOS ---
            // Chuyển JsonNode thành Map. TreeMap sẽ tự động sắp xếp các Key theo thứ tự A-Z
            Map<String, Object> dataMap = objectMapper.convertValue(dataNode, new TypeReference<>() {
            });
            TreeMap<String, Object> sortedMap = new TreeMap<>(dataMap);

            StringBuilder stringBuilder = new StringBuilder();
            for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
                // payOS quy định: Bỏ qua các trường có giá trị null hoặc rỗng, hoặc danh sách
                // mảng
                if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                    if (!stringBuilder.isEmpty()) {
                        stringBuilder.append("&");
                    }
                    stringBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            String dataToHash = stringBuilder.toString();
            log.info("==> [Security] Chuoi tho da sap xep chuan payOS: {}", dataToHash);

            // --- STEP 2: HASH DATA (HMAC-SHA256) ---
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hashBytes = sha256_HMAC.doFinal(dataToHash.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            String generatedSignature = hexString.toString();

            // --- STEP 3: VERIFY SIGNATURE ---
            return generatedSignature.equals(signatureFromPayOs);

        } catch (Exception e) {
            log.error("==> [Security] Critical error while verifying signature: ", e);
            return false;
        }
    }
}