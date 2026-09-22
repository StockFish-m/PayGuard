package com.payguard.engine.controller.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.service.payment.PaymentWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentProvider paymentProvider;
    private final ObjectMapper objectMapper;
    private final PaymentWebhookService paymentWebhookService;
    private final TransactionRepository transactionRepository;

    public WebhookController(PaymentProvider paymentProvider,
                             ObjectMapper objectMapper,
                             PaymentWebhookService paymentWebhookService,
                             TransactionRepository transactionRepository) {
        this.paymentProvider = paymentProvider;
        this.objectMapper = objectMapper;
        this.paymentWebhookService = paymentWebhookService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> receivePayOsWebhook(@RequestBody String requestBody) {
        log.info("==> [Webhook] Received real-time payment notification from payment gateway!");

        try {
            JsonNode rootNode = objectMapper.readTree(requestBody);
            String signatureFromPayOs = rootNode.path("signature").asText();
            JsonNode dataNode = rootNode.path("data");

            // 1. Tự động kiểm tra tính toàn vẹn qua PaymentProvider (Chống Hacker)
            boolean isSafe = paymentProvider.verifyWebhookSignature(requestBody, signatureFromPayOs);

            if (!isSafe) {
                log.warn("==> [Security Alert] 🚨 INVALID SIGNATURE! Rejecting webhook payload.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Signature");
            }

            // 2. Bóc tách dữ liệu từ PayOS Webhook
            long payOsOrderCode = dataNode.path("orderCode").asLong();
            long payOsAmount = dataNode.path("amount").asLong();
            String payOsStatus = dataNode.path("status").asText(); // "PAID", "CANCELLED", "FAILED", "EXPIRED"

            Transaction dbTxn = transactionRepository
                    .findByPayOsOrderCode(payOsOrderCode)
                    .orElse(null);

            if (dbTxn == null) {
                log.error("==> [Webhook] Order with PayOS code {} not found in DB!", payOsOrderCode);
                return ResponseEntity.ok("success"); // Vẫn trả 200 để PayOS không retry
            }

            log.info("==> [Webhook] Valid signature! Processing order {} with status='{}', amount={}",
                    dbTxn.getOrderCode(), payOsStatus, payOsAmount);

            // 3. Xử lý webhook theo luồng Real-time chuyên biệt (tách biệt hoàn toàn với batch ReconciliationProcessor)
            paymentWebhookService.processWebhookPayment(
                    dbTxn.getOrderCode(), payOsStatus, payOsAmount);

            return ResponseEntity.ok("success");

        } catch (Exception e) {
            log.error("==> [Webhook] System error while processing payload: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Bad Request");
        }
    }
}