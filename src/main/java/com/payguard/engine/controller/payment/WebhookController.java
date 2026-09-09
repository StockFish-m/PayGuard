package com.payguard.engine.controller.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.service.reconciliation.ReconciliationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/payment")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentProvider paymentProvider;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository; // Gọi DB để check trạng thái cũ
    private final ReconciliationProcessor reconciliationProcessor; // Gọi bộ não để update DB

    public WebhookController(PaymentProvider paymentProvider,
            ObjectMapper objectMapper,
            TransactionRepository transactionRepository,
            ReconciliationProcessor reconciliationProcessor) {
        this.paymentProvider = paymentProvider;
        this.objectMapper = objectMapper;
        this.transactionRepository = transactionRepository;
        this.reconciliationProcessor = reconciliationProcessor;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> receivePayOsWebhook(@RequestBody String requestBody) {
        log.info("==> [Webhook] Received real-time payment notification!");

        try {
            JsonNode rootNode = objectMapper.readTree(requestBody);
            String signatureFromPayOs = rootNode.path("signature").asText();
            JsonNode dataNode = rootNode.path("data");

            // 1. Tự động kiểm tra tính toàn vẹn qua PaymentProvider (Chống Hacker)
            boolean isSafe = paymentProvider.verifyWebhookSignature(requestBody, signatureFromPayOs);

            if (!isSafe) {
                log.warn("==> [Security Alert] 🚨 INVALID SIGNATURE! Rejecting DB update.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Signature");
            }

            // 2. Lấy dữ liệu thật từ payOS để cập nhật
            long payOsOrderCode = dataNode.path("orderCode").asLong();
            long payOsAmount = dataNode.path("amount").asLong();
            String dbOrderCode = "TXN-00" + payOsOrderCode; // Map số Long thành chữ TXN

            // 3. Gọi MySQL để lấy trạng thái cũ và ném vào Processor xử lý
            Optional<Transaction> dbTxnOpt = transactionRepository.findById(dbOrderCode);
            if (dbTxnOpt.isPresent()) {
                Transaction dbTxn = dbTxnOpt.get();
                log.info("==> [Webhook] Valid signature! Passing order {} to Processor for handling...", dbOrderCode);

                // Mượn sức mạnh của bộ não để ghi đè trạng thái SUCCESS xuống DB
                reconciliationProcessor.processRow(dbOrderCode, payOsAmount, dbTxn.getStatus(), dbTxn.getAmount());
            } else {
                log.error("==> [Webhook] Payment received but order {} does not exist in Database!", dbOrderCode);
            }

            return ResponseEntity.ok("success");

        } catch (Exception e) {
            log.error("==> [Webhook] System error while processing payload: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Bad Request");
        }
    }
}