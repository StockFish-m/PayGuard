package com.payguard.engine.service.payment;

import com.payguard.engine.dto.CheckoutRequest;
import com.payguard.engine.dto.CheckoutResponse;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.provider.dto.PaymentRequest;
import com.payguard.engine.provider.dto.PaymentResponse;
import com.payguard.engine.provider.impl.PayOsPaymentProvider;
import com.payguard.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final TransactionRepository transactionRepository;
    private final PaymentProvider paymentProvider;
    private final ObjectMapper objectMapper;

    public CheckoutService(TransactionRepository transactionRepository,
                           PaymentProvider paymentProvider,
                           ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.paymentProvider = paymentProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CheckoutResponse processCheckout(CheckoutRequest request,
                                            String appId,
                                            String idempotencyKey) {

        // ── PHÒNG THỦ 1: Idempotency check trong DB ──────────────────────────
        Optional<Transaction> existingTxn =
                transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTxn.isPresent()) {
            log.info("==> [Checkout] Idempotency Hit! Returning cached response for key: {}",
                    idempotencyKey);
            try {
                return objectMapper.readValue(
                        existingTxn.get().getResponseBody(), CheckoutResponse.class);
            } catch (Exception e) {
                log.error("==> [Checkout] Failed to parse cached response for key: {}",
                        idempotencyKey, e);
                throw new RuntimeException("Internal error processing duplicate request");
            }
        }

        // ── SINH ORDER CODE ───────────────────────────────────────────────────
        // Dùng Snowflake từ PaymentProvider — đảm bảo unique, không trùng, đúng 53-bit
        // PaymentRequest với transactionId=null sẽ trigger fallback Snowflake trong extractOrderCode()
        long payOsOrderCode = ((PayOsPaymentProvider) paymentProvider).extractOrderCode(null);

        String dbOrderCode = "TXN-" + payOsOrderCode; // VD: TXN-1749123456789012

        log.info("==> [Checkout] New payment. PayOsCode={}, DbCode={}, AppId={}",
                payOsOrderCode, dbOrderCode, appId);

        // ── PHÒNG THỦ 2: Lưu DB TRƯỚC khi gọi PayOS ─────────────────────────
        // Lý do: Nếu gọi PayOS xong rồi crash trước khi save → tiền mất, DB không biết
        Transaction newTxn = Transaction.builder()
                .orderCode(dbOrderCode)
                .payOsOrderCode(payOsOrderCode)
                .amount(request.amount())
                .status(TransactionStatus.PENDING)
                .appId(appId)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            transactionRepository.save(newTxn);
            log.info("==> [Checkout] Pre-saved transaction {} with PENDING status.", dbOrderCode);

        } catch (DataIntegrityViolationException e) {
            // Race condition: 2 request cùng idempotencyKey chạy song song
            // Request kia đã save trước → lấy response đã cache trả về
            log.warn("==> [Checkout] Race condition on idempotency key: {}. Fetching saved response.",
                    idempotencyKey);
            Transaction saved = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException(
                            "Idempotency conflict but no record found for key: " + idempotencyKey));
            try {
                return objectMapper.readValue(saved.getResponseBody(), CheckoutResponse.class);
            } catch (Exception parseEx) {
                throw new RuntimeException("Internal error resolving idempotency race condition");
            }
        }

        // ── GỌI PAYOS ────────────────────────────────────────────────────────
        // Truyền payOsOrderCode dưới dạng String để extractOrderCode() bóc ra đúng số
        PaymentRequest providerReq = new PaymentRequest(
                String.valueOf(payOsOrderCode),
                BigDecimal.valueOf(request.amount()),
                request.description(),
                request.returnUrl(),
                request.cancelUrl()
        );

        PaymentResponse providerRes;
        try {
            providerRes = paymentProvider.createPaymentLink(providerReq);
        } catch (Exception e) {
            // PayOS thất bại → đánh dấu FAILED trong DB, không để PENDING treo mãi
            newTxn.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(newTxn);
            log.error("==> [Checkout] PayOS call failed for order {}. Marked as FAILED.", dbOrderCode, e);
            throw new RuntimeException("Payment gateway error: " + e.getMessage(), e);
        }

        // ── CẬP NHẬT RESPONSE VÀO DB ─────────────────────────────────────────
        CheckoutResponse response = new CheckoutResponse(
                dbOrderCode,
                providerRes.checkoutUrl(),
                providerRes.qrCodeString()
        );

        try {
            newTxn.setResponseBody(objectMapper.writeValueAsString(response));
            transactionRepository.save(newTxn);
            log.info("==> [Checkout] Transaction {} finalized with checkout URL.", dbOrderCode);
        } catch (Exception e) {
            log.error("==> [Checkout] Failed to update response body for order {}", dbOrderCode, e);
            throw new RuntimeException("Failed to finalize transaction data");
        }

        return response;
    }
}