package com.payguard.engine.entity;

import com.payguard.engine.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_order_code", columnList = "order_code")
})
public class Transaction {

    @Id
    @Column(name = "order_code", length = 50)
    private String orderCode; // "TXN-001234" — mã nội bộ PayGuard

    @Column(name = "pay_os_order_code", unique = true, nullable = false)
    private Long payOsOrderCode; // Số nguyên gửi sang PayOS — dùng để map webhook về

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Transaction() {
    }

    public Transaction(String orderCode, Long payOsOrderCode, Long amount, TransactionStatus status,
                       String appId, String idempotencyKey, String responseBody,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.orderCode = orderCode;
        this.payOsOrderCode = payOsOrderCode;
        this.amount = amount;
        this.status = status;
        this.appId = appId;
        this.idempotencyKey = idempotencyKey;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}