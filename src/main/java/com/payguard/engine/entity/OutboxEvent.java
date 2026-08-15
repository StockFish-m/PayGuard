package com.payguard.engine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Data;

@Data
@Entity
// 🚀 TỐI ƯU HIỆU NĂNG: Đánh Index cho cột status để truy vấn siêu tốc độ, không
// bị quét toàn bảng
@Table(name = "outbox_events", indexes = {
        // Gộp 2 cột vào 1 Composite Index duy nhất:
        @Index(name = "idx_outbox_status_next_retry", columnList = "status, next_retry_at")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType; // Ví dụ: "TRANSACTION"

    @Column(nullable = false, length = 50)
    private String aggregateId; // Ví dụ: "TXN-004"

    @Column(nullable = false, length = 100)
    private String eventType; // Ví dụ: "PAYMENT_SUCCESS"

    // 🚀 TỐI ƯU LƯU TRỮ: Mở rộng khoang chứa JSON
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, PROCESSING, PROCESSED, FAILED, DEAD

    @Column(nullable = false)
    private int retryCount = 0; // Đếm số lần thử lại

    @Column(columnDefinition = "TEXT")
    private String lastError; // Lưu lý do lỗi để dễ debug

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processingAt; // Đánh dấu lúc Worker nhấc lên xử lý

    private LocalDateTime nextRetryAt; // Hẹn giờ thử lại nếu lỗi

    private LocalDateTime processedAt; // Đánh dấu lúc hoàn thành

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
    }

}