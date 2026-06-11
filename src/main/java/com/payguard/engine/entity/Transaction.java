package com.payguard.engine.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions") // Tên bảng dưới MySQL
public class Transaction {

    @Id
    private String orderCode; // Mã đơn hàng (Ví dụ: TXN-001)
    private Long amount; // Số tiền hệ thống ghi nhận
    private String status; // Trạng thái (PENDING, SUCCESS, FAILED)

    // --- Constructor trống bắt buộc cho JPA ---
    public Transaction() {
    }

    public Transaction(String orderCode, Long amount, String status) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}