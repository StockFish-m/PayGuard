package com.payguard.engine.provider.dto;

public record ReconciliationTransactionDTO(
        String orderCode, // Mã đơn hàng chuẩn hóa (VD: "TXN-00123")
        long amount, // Số tiền giao dịch ghi nhận
        String status // Trạng thái giao dịch từ phía cổng thanh toán
) {
}
