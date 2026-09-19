// === src/main/java/com/payguard/engine/entity/MerchantApp.java ===
package com.payguard.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "merchant_apps")
public class MerchantApp {

    @Id
    @Column(name = "app_id", length = 50)
    private String appId; // VD: "ASTRACINE", "NEARBUY"

    @Column(name = "app_name", nullable = false)
    private String appName;

    // Chìa khóa để Đối tác xác thực khi gọi API tạo mã QR của PayGuard (Inbound)
    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    // Chìa khóa để PayGuard ký HMAC-SHA256 khi bắn Webhook trả kết quả (Outbound)
    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    // URL đích mà Outbox Worker sẽ nhắm bắn tới
    @Column(name = "webhook_url", nullable = false)
    private String webhookUrl;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MerchantApp() {
        this.createdAt = LocalDateTime.now();
        // Tự động sinh key ngẫu nhiên an toàn nếu chưa có
        this.apiKey = "pk_live_" + UUID.randomUUID().toString().replace("-", "");
        this.secretKey = "whsec_" + UUID.randomUUID().toString().replace("-", "");
    }
}