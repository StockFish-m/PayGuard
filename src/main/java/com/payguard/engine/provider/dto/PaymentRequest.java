package com.payguard.engine.provider.dto;

import java.math.BigDecimal;

public record PaymentRequest(
        String transactionId, // Mã giao dịch gốc từ bảng transactions
        BigDecimal amount, // Tổng tiền
        String description, // Mô tả đơn hàng (VD: "Thanh toan don Zalo 123")
        String returnUrl, // Nơi điều hướng về sau khi khách thanh toán xong
        String cancelUrl // Nơi điều hướng về nếu khách bấm hủy
) {

}
