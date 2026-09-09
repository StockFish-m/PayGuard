package com.payguard.engine.provider.dto;

public record PaymentResponse(
        String providerTransactionId, // Mã giao dịch phía đối tác sinh ra
        String checkoutUrl, // Link mở trang thanh toán
        String qrCodeString // Chuỗi Data QR để chủ shop render thẳng mã QR nếu cần
) {

}
