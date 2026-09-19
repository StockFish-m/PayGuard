package com.payguard.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class PayGuardSecurityUtil {

    private static final Logger log = LoggerFactory.getLogger(PayGuardSecurityUtil.class);

    /**
     * Băm bất kỳ chuỗi dữ liệu nào bằng thuật toán HMAC-SHA256
     * 
     * @param data Dữ liệu thô (có thể là chuỗi đã sắp xếp A-Z hoặc nguyên một cục
     *             JSON)
     * @param key  Chìa khóa bí mật để ký
     * @return Chuỗi Hex (Chữ ký điện tử)
     */
    public String signHmacSha256(String data, String key) {
        try {
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);

            byte[] hashBytes = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("==> [SecurityUtil] Failed to generate HMAC-SHA256 signature", e);
            throw new RuntimeException("Encryption error during payload signing", e);
        }
    }
}