package com.payguard.engine.entity;

import com.payguard.engine.enums.TransactionStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_txn_app_id", columnList = "app_id"),
        @Index(name = "idx_txn_status", columnList = "status")
})
public class Transaction {

    @Column(name = "app_id", nullable = false, length = 50)
    private String appId;

    @Id
    @Column(name = "order_code", length = 50)
    private String orderCode; // Mã đơn hàng (Ví dụ: TXN-001)

    @Column(name = "amount", nullable = false)
    private Long amount; // Số tiền hệ thống ghi nhận

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status; // Trạng thái (PENDING, SUCCESS, FAILED, AMOUNT_MISMATCH)

    // Khóa chống trùng lặp request ở tầng Database
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    // Lưu trữ JSON response để trả về ngay cho client nếu bị trùng request
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- Constructor trống bắt buộc cho JPA ---
    public Transaction() {
    }

    public Transaction(String orderCode, Long amount, TransactionStatus status, String appId) {
        this.orderCode = orderCode;
        this.amount = amount;
        this.status = status;
        this.appId = appId;
    }

    public Transaction(String orderCode, Long amount, TransactionStatus status, String appId, String idempotencyKey) {
        this.orderCode = orderCode;
        this.amount = amount;
        this.status = status;
        this.appId = appId;
        this.idempotencyKey = idempotencyKey;
    }

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

    // --- Getter và Setter ---
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}