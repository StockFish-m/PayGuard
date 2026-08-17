package com.payguard.engine.entity;

import com.payguard.engine.enums.OutboxStatus;
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
    private String aggregateType; // e.g. "TRANSACTION"

    @Column(nullable = false, length = 50)
    private String aggregateId; // e.g. "TXN-004"

    @Column(nullable = false, length = 100)
    private String eventType; // e.g. "PAYMENT_SUCCESS"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status; // PENDING, PROCESSING, PROCESSED, FAILED, DEAD

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processingAt;

    private LocalDateTime nextRetryAt;

    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
    }

}