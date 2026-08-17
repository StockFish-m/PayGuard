package com.payguard.engine.entity;

import com.payguard.engine.enums.TransactionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Data
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key")
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Chốt chặn cứng ở DB tầng vật lý: cấm trùng lặp token yêu cầu thanh toán
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotency_key;

    // Mã giao dịch trả về từ cổng thanh toán đối tác (VNPAY/Stripe...)
    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    // Mã đơn hàng thuộc hệ thống lõi của bạn (ví dụ: mã đơn đặt vé phim)
    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Trạng thái giao dịch: PENDING, SUCCESS, FAILED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TransactionStatus status;

    // Lưu trữ toàn bộ JSON response từ Gateway để trả về ngay cho client nếu bị
    // trùng request
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
