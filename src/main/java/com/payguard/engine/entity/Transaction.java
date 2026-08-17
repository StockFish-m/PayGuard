package com.payguard.engine.entity;

import com.payguard.engine.enums.TransactionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions") // Tên bảng dưới MySQL
public class Transaction {

    @Id
    private String orderCode; // Mã đơn hàng (Ví dụ: TXN-001)
    private Long amount; // Số tiền hệ thống ghi nhận

    @Enumerated(EnumType.STRING)
    private TransactionStatus status; // Trạng thái (PENDING, SUCCESS, FAILED, AMOUNT_MISMATCH)

    // --- Constructor trống bắt buộc cho JPA ---
    public Transaction() {
    }

    public Transaction(String orderCode, Long amount, TransactionStatus status) {
        this.orderCode = orderCode;
        this.amount = amount;
        this.status = status;
    }

    // --- Getter và Setter ---
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
}