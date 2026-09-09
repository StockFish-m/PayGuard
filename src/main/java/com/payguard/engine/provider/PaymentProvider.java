package com.payguard.engine.provider;

import com.payguard.engine.provider.dto.PaymentRequest;
import com.payguard.engine.provider.dto.PaymentResponse;
import com.payguard.engine.provider.dto.ReconciliationTransactionDTO;

import java.util.List;

public interface PaymentProvider {

    /**
     * Khai báo tên định danh của Provider (VD: "PAYOS", "MOMO").
     */
    String getProviderId();

    /**
     * Sinh ra đường link hoặc mã QR để gửi cho khách.
     */
    PaymentResponse createPaymentLink(PaymentRequest request);

    /**
     * Lá chắn thép: Xác minh chữ ký dữ liệu từ Webhook.
     * Trả về true nếu dữ liệu đúng chuẩn của đối tác, false nếu là hacker giả mạo.
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * Lấy danh sách giao dịch từ cổng thanh toán để chạy đối soát định kỳ.
     */
    List<ReconciliationTransactionDTO> fetchReconciliationTransactions();
}