package com.payguard.engine.worker;

import com.payguard.engine.entity.MerchantApp;
import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.event.OutboxWakeupEvent;
import com.payguard.engine.processor.OutboxProcessor;
import com.payguard.engine.repository.MerchantAppRepository;
import com.payguard.engine.util.PayGuardSecurityUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxProcessor processor;
    private final RestClient restClient;
    private final MerchantAppRepository merchantAppRepository;
    private final PayGuardSecurityUtil securityUtil;

    public OutboxRelayWorker(OutboxProcessor processor,
            RestClient outboxRestClient,
            MerchantAppRepository merchantAppRepository,
            PayGuardSecurityUtil securityUtil) {
        this.processor = processor;
        this.restClient = outboxRestClient;
        this.merchantAppRepository = merchantAppRepository;
        this.securityUtil = securityUtil;
    }

    /**
     * 1. FAST PATH (Real-time): Đánh thức ngay khi DB vừa commit xong OutboxEvent
     * mới.
     * Chạy bất đồng bộ (@Async) trên Virtual Thread để không chặn luồng chính.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxWakeup(OutboxWakeupEvent wakeupEvent) {
        log.info("⚡ [Outbox Worker] Wakeup signal received for event ID: {}. Processing immediately!",
                wakeupEvent.outboxEventId());
        relayEvents();
    }

    /**
     * 2. SAFETY NET (Polling): Quét định kỳ mỗi 5s đề phòng sự kiện retry hoặc sót
     * đơn.
     */
    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        // 1. PHA 1 (Chớp nhoáng): Lấy data và khóa dòng trong DB (Transaction đã đóng
        // ngay sau lệnh này)
        List<OutboxEvent> events = processor.claimAndMarkProcessing();
        if (events.isEmpty()) {
            return;
        }

        log.info("Starting relay of {} Outbox events to downstream merchants...", events.size());

        // 2. PHA 2 (Nguy hiểm): Gọi mạng Internet (Lúc này DB Connection đã được giải
        // phóng hoàn toàn)
        for (OutboxEvent event : events) {
            try {
                String appId = event.getAppId();
                if (appId == null || appId.isBlank()) {
                    log.error("Outbox event ID {} is missing appId. Cannot determine downstream merchant.",
                            event.getId());
                    processor.markAsFailed(event.getId(), "Missing appId in OutboxEvent");
                    continue;
                }

                // 2.1. Truy vấn trực tiếp thông tin đối tác (webhookUrl và secretKey) từ bảng
                // MerchantApp
                MerchantApp merchantApp = merchantAppRepository.findById(appId).orElse(null);
                if (merchantApp == null) {
                    log.error("Merchant app '{}' not found for Outbox event ID: {}", appId, event.getId());
                    processor.markAsFailed(event.getId(), "Merchant app not found: " + appId);
                    continue;
                }

                if (!merchantApp.isActive()) {
                    log.warn("Merchant app '{}' is inactive. Skipping Outbox event ID: {}", appId, event.getId());
                    processor.markAsFailed(event.getId(), "Merchant app is inactive: " + appId);
                    continue;
                }

                String webhookUrl = merchantApp.getWebhookUrl();
                if (webhookUrl == null || webhookUrl.isBlank()) {
                    log.error("Merchant app '{}' has no webhookUrl configured. Event ID: {}", appId, event.getId());
                    processor.markAsFailed(event.getId(), "Missing webhookUrl for merchant: " + appId);
                    continue;
                }

                // 2.2. Ký HMAC-SHA256 bảo mật bằng secret_key riêng của đối tác lấy từ
                // MerchantApp
                String signature = securityUtil.signHmacSha256(event.getPayload(), merchantApp.getSecretKey());

                // 2.3. Bắn Webhook HTTP POST tới đúng webhookUrl của đối tác
                restClient.post()
                        .uri(webhookUrl)
                        .header("Content-Type", "application/json")
                        .header("X-Event-ID", String.valueOf(event.getId())) // Header hỗ trợ đối tác chống trùng lặp
                                                                             // (Idempotency)
                        .header("X-Event-Type", event.getEventType())
                        .header("X-PayGuard-Signature", signature) // Chữ ký số điện tử để đối tác xác thực
                        .body(event.getPayload())
                        .retrieve()
                        .toBodilessEntity(); // Chỉ cần biết HTTP 2xx thành công, không cần parse body

                // 3a. PHA 3 (Thành công): Chốt sổ
                processor.markAsProcessed(event.getId());
                log.info("Successfully relayed event ID: {} to merchant '{}' at {}", event.getId(), appId, webhookUrl);

            } catch (Exception e) {
                // 3b. PHA 3 (Thất bại): Bóc tách lỗi chí mạng và phán xử Retry
                String errorMessage = extractErrorMessage(e);
                log.warn("Failed to relay event ID: {}. Cause: {}", event.getId(), errorMessage);

                processor.markAsFailed(event.getId(), errorMessage);
            }
        }
    }

    /**
     * Helper bóc tách lỗi để lưu xuống DB: Ngắn gọn, súc tích nhưng cung cấp đủ ngữ
     * cảnh cho Admin.
     */
    private String extractErrorMessage(Exception e) {
        String message;

        if (e instanceof RestClientResponseException httpEx) {
            // Lỗi do máy chủ đối tác trả về HTTP 4xx (Lỗi payload) hoặc 5xx (Server họ sập)
            message = String.format("HTTP %d: %s",
                    httpEx.getStatusCode().value(),
                    httpEx.getStatusText());
        } else if (e instanceof ResourceAccessException networkEx) {
            // Lỗi do Timeout (3s/5s) hoặc từ chối kết nối (Connection Refused / Rớt mạng)
            Throwable rootCause = networkEx.getRootCause();
            String causeName = (rootCause != null) ? rootCause.getClass().getSimpleName() : "NetworkError";
            message = String.format("[I/O Error - %s] %s", causeName, networkEx.getMessage());
        } else {
            // Các lỗi ngoại lệ khác (ví dụ: lỗi parse URI, lỗi bộ nhớ...)
            message = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        // Cắt xén (Truncate) nếu chuỗi lỗi quá dài, tránh làm lỗi câu lệnh UPDATE xuống
        // MySQL (giới hạn 500 ký tự)
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }
}