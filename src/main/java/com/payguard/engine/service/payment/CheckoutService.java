package com.payguard.engine.service.payment;

import com.payguard.engine.dto.CheckoutRequest;
import com.payguard.engine.dto.CheckoutResponse;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.provider.dto.PaymentRequest;
import com.payguard.engine.provider.dto.PaymentResponse;
import com.payguard.engine.provider.dto.ReconciliationTransactionDTO;
import com.payguard.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Service xử lý thanh toán (Checkout) theo kiến trúc chuẩn Enterprise & Provider-Agnostic:
 * 1. Không dùng @Transactional ở mức method: Tránh giữ DB Connection qua cuộc gọi HTTP sang cổng thanh toán.
 * 2. Phân tách Transaction tinh gọn bằng TransactionTemplate: Commit PENDING ngay trước khi gọi mạng,
 *    và Commit kết quả (SUCCESS / FAILED) ngay sau khi có phản hồi.
 * 3. Tự phục hồi lỗi (Self-Healing Idempotency): Nếu server crash dở dang giữa 2 lần save khiến
 *    bản ghi có status PENDING nhưng response_body == null, hệ thống tự động đối soát với cổng thanh toán
 *    để đồng bộ trạng thái, ngăn chặn triệt để NullPointerException/500 khi client retry.
 * 4. Độc lập hoàn toàn với nhà cung cấp (Vendor-Agnostic): Giao tiếp 100% qua PaymentProvider abstraction,
 *    không phụ thuộc hay trói buộc logic vào bất kỳ cổng cụ thể nào.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final TransactionRepository transactionRepository;
    private final PaymentProvider paymentProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public CheckoutService(TransactionRepository transactionRepository,
                           PaymentProvider paymentProvider,
                           ObjectMapper objectMapper,
                           TransactionTemplate transactionTemplate) {
        this.transactionRepository = transactionRepository;
        this.paymentProvider = paymentProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public CheckoutResponse processCheckout(CheckoutRequest request,
                                            String appId,
                                            String idempotencyKey) {

        // ── PHÒNG THỦ 1: Kiểm tra Idempotency trong DB ─────────────────────────
        Optional<Transaction> existingTxn = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTxn.isPresent()) {
            Transaction existing = existingTxn.get();
            log.info("==> [Checkout] Idempotency Hit for key: {}, orderCode: {}",
                    idempotencyKey, existing.getOrderCode());

            // KỊCH BẢN LÝ TƯỞNG: Đã có response_body lưu từ trước -> Deserialize và trả về ngay lập tức
            if (existing.getResponseBody() != null && !existing.getResponseBody().isBlank()) {
                try {
                    return objectMapper.readValue(existing.getResponseBody(), CheckoutResponse.class);
                } catch (Exception e) {
                    log.error("==> [Checkout] Failed to parse cached response for key: {}", idempotencyKey, e);
                    throw new RuntimeException("Internal error processing duplicate request");
                }
            }

            // KỊCH BẢN CRASH / GIÁN ĐOẠN: Bản ghi tồn tại nhưng response_body == null
            // Do server bị tắt/restart hoặc timeout giữa chừng trước khi kịp lưu response.
            // -> Kích hoạt cơ chế Self-Healing để đối soát và xử lý an toàn
            return recoverPendingTransaction(existing);
        }

        // ── SINH MÃ ĐƠN HÀNG QUA INTERFACE PAYMENT PROVIDER ───────────────────
        long orderCodeNumber = paymentProvider.generateOrderCode();
        String dbOrderCode = "TXN-" + orderCodeNumber; // Định dạng hiển thị nội bộ DB (VD: TXN-1749123456789012)

        log.info("==> [Checkout] New payment initiated. OrderCodeNum={}, DbCode={}, AppId={}",
                orderCodeNumber, dbOrderCode, appId);

        // ── PHÒNG THỦ 2: TRANSACTION 1 - Lưu PENDING TRƯỚC khi gọi cổng thanh toán ──
        // Commit ngay lập tức để giải phóng DB Connection về HikariCP pool
        Transaction newTxn = Transaction.builder()
                .orderCode(dbOrderCode)
                .payOsOrderCode(orderCodeNumber)
                .amount(request.amount())
                .status(TransactionStatus.PENDING)
                .appId(appId)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            transactionTemplate.executeWithoutResult(status -> transactionRepository.save(newTxn));
            log.info("==> [Checkout] Transaction 1 COMMITTED: Pre-saved {} as PENDING. Connection released.", dbOrderCode);

        } catch (DataIntegrityViolationException e) {
            // Xử lý Race Condition: Khi 2 request cùng idempotencyKey lao vào đồng thời
            // Request kia vừa kịp commit -> Tìm lại bản ghi và trả kết quả
            log.warn("==> [Checkout] Race condition detected on idempotency key: {}. Recovering...", idempotencyKey);
            Transaction saved = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Idempotency conflict but record not found for key: " + idempotencyKey));

            if (saved.getResponseBody() != null && !saved.getResponseBody().isBlank()) {
                try {
                    return objectMapper.readValue(saved.getResponseBody(), CheckoutResponse.class);
                } catch (Exception parseEx) {
                    throw new RuntimeException("Internal error resolving idempotency race condition");
                }
            }
            return recoverPendingTransaction(saved);
        }

        // ── GỌI CỔNG THANH TOÁN QUA HTTP (HOÀN TOÀN KHÔNG GIỮ DB CONNECTION) ──
        PaymentRequest providerReq = new PaymentRequest(
                String.valueOf(orderCodeNumber),
                BigDecimal.valueOf(request.amount()),
                request.description(),
                request.returnUrl(),
                request.cancelUrl());

        PaymentResponse providerRes;
        try {
            providerRes = paymentProvider.createPaymentLink(providerReq);
        } catch (Exception e) {
            // ── TRANSACTION 2 (FAIL PATH): Cập nhật FAILED độc lập ────────────
            // Nhờ không có @Transactional bọc ngoài method, bản ghi FAILED này được commit chắc chắn vào DB
            transactionTemplate.executeWithoutResult(status -> {
                newTxn.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(newTxn);
            });
            log.error("==> [Checkout] Payment provider call failed for order {}. Marked as FAILED in DB.", dbOrderCode, e);
            throw new RuntimeException("Payment gateway error: " + e.getMessage(), e);
        }

        // ── TRANSACTION 2 (SUCCESS PATH): Cập nhật Response và Checkout URL ────
        CheckoutResponse response = new CheckoutResponse(
                dbOrderCode,
                providerRes.checkoutUrl(),
                providerRes.qrCodeString());

        try {
            String responseJson = objectMapper.writeValueAsString(response);
            transactionTemplate.executeWithoutResult(status -> {
                newTxn.setResponseBody(responseJson);
                transactionRepository.save(newTxn);
            });
            log.info("==> [Checkout] Transaction 2 COMMITTED: Order {} finalized with checkout URL.", dbOrderCode);
        } catch (Exception e) {
            log.error("==> [Checkout] Failed to serialize or update response body for order {}", dbOrderCode, e);
            throw new RuntimeException("Failed to finalize transaction data", e);
        }

        return response;
    }

    /**
     * Cơ chế Self-Healing: Tự phục hồi khi phát hiện request retry rơi vào bản ghi PENDING bị mồ côi
     * (do crash máy chủ hoặc rớt mạng giữa 2 lần lưu).
     */
    private CheckoutResponse recoverPendingTransaction(Transaction pendingTxn) {
        log.warn("==> [Checkout] Initiating recovery for orphaned transaction: {}, orderCodeNum: {}",
                pendingTxn.getOrderCode(), pendingTxn.getPayOsOrderCode());

        ReconciliationTransactionDTO externalTxn = null;
        try {
            // Truyền trực tiếp mã đơn dạng chuỗi số sang provider abstraction
            externalTxn = paymentProvider.fetchTransaction(String.valueOf(pendingTxn.getPayOsOrderCode()));
        } catch (Exception e) {
            log.warn("==> [Checkout] Unable to query payment provider for order {}: {}",
                    pendingTxn.getPayOsOrderCode(), e.getMessage());
        }

        if (externalTxn != null) {
            log.info("==> [Checkout] Payment provider order {} found with status: {}",
                    pendingTxn.getPayOsOrderCode(), externalTxn.status());

            // Đơn hàng đã được thanh toán thành công trên cổng thanh toán
            // -> Cập nhật DB sang SUCCESS, lưu response_body và trả về CheckoutResponse trực tiếp cho client
            if ("PAID".equalsIgnoreCase(externalTxn.status())) {
                CheckoutResponse paidResponse = new CheckoutResponse(
                        pendingTxn.getOrderCode(),
                        null,
                        null);
                transactionTemplate.executeWithoutResult(status -> {
                    pendingTxn.setStatus(TransactionStatus.SUCCESS);
                    try {
                        pendingTxn.setResponseBody(objectMapper.writeValueAsString(paidResponse));
                    } catch (Exception ignored) {
                    }
                    transactionRepository.save(pendingTxn);
                });
                log.info("==> [Checkout] Incomplete order {} was already PAID on payment provider. Recovered as SUCCESS.",
                        pendingTxn.getOrderCode());
                return paidResponse;
            }

            if ("CANCELLED".equalsIgnoreCase(externalTxn.status())) {
                transactionTemplate.executeWithoutResult(status -> {
                    pendingTxn.setStatus(TransactionStatus.FAILED);
                    transactionRepository.save(pendingTxn);
                });
                throw new IllegalStateException("Đơn hàng trên cổng thanh toán đã bị hủy.");
            }

            // Nếu đơn trên cổng thanh toán vẫn PENDING nhưng hệ thống mất link do crash trước khi lưu
            throw new IllegalStateException("Giao dịch đang chờ thanh toán trên cổng thanh toán nhưng bị gián đoạn lưu trữ tại máy chủ. Vui lòng liên hệ CSKH.");
        }

        // Trường hợp cổng thanh toán hoàn toàn chưa có thông tin đơn này (sự cố sập xảy ra TRƯỚC khi gửi request tới provider)
        // -> Đánh dấu FAILED bản ghi mồ côi để giải phóng trạng thái, cho phép khách hàng tạo đơn thanh toán mới
        transactionTemplate.executeWithoutResult(status -> {
            pendingTxn.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(pendingTxn);
        });
        log.info("==> [Checkout] Orphaned transaction {} marked as FAILED.", pendingTxn.getOrderCode());

        throw new IllegalStateException("Giao dịch trước đó bị gián đoạn do sự cố mạng/máy chủ. Đơn hàng dở dang đã được đóng, vui lòng thực hiện lại.");
    }
}