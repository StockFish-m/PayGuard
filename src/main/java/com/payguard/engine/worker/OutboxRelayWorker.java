package com.payguard.engine.worker;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.processor.OutboxProcessor;
import com.payguard.engine.util.PayGuardSecurityUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxProcessor processor;
    private final RestClient restClient;
    private final PayGuardSecurityUtil securityUtil;

    @Value("${payguard.downstream.webhook-url:http://localhost:8081/webhook}")
    private String webhookUrl;
    // TODO: Tạm thời dùng key cố định. Sau này sẽ query từ bảng MerchantApp
    @Value("${payguard.downstream.secret-key:AstraCine_SecretKey_123456789}")
    private String downstreamSecretKey;

    public OutboxRelayWorker(OutboxProcessor processor, RestClient outboxRestClient,
            PayGuardSecurityUtil securityUtil) {
        this.processor = processor;
        this.restClient = outboxRestClient;
        this.securityUtil = securityUtil;
    }

    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        // 1. PHA 1 (Chớp nhoáng): Lấy data và khóa dòng trong DB (Transaction đã đóng
        // ngay sau lệnh này)
        List<OutboxEvent> events = processor.claimAndMarkProcessing();
        if (events.isEmpty()) {
            return;
        }

        log.info("Starting relay of {} Outbox events to Downstream...", events.size());

        // 2. PHA 2 (Nguy hiểm): Gọi mạng Internet (Lúc này DB Connection đã được giải
        // phóng hoàn toàn)
        for (OutboxEvent event : events) {
            try {
                // 2.1. Chuẩn bị chuỗi chữ ký (Payload đã được sắp xếp A-Z ở OutboxProcessor)
                String payloadJson = event.getPayload();
                String signature = securityUtil.signHmacSha256(payloadJson, downstreamSecretKey);

                // Ghi log để debug (Che bớt chuỗi ký tự để tránh lộ trong log)
                log.info("--> Signing Payload (Event {}): {}", event.getId(), signature.substring(0, 16) + "...");

                restClient.post()
                        .uri(webhookUrl)
                        .header("Content-Type", "application/json")
                        .header("X-Event-ID", String.valueOf(event.getId())) // Header hỗ trợ đối tác chống trùng lặp
                                                                             // (Idempotency)
                        .header("X-Event-Type", event.getEventType())
                        .header("X-PayGuard-Signature", signature) // <--- LÁ CHẮN ĐƯỢC KÍCH HOẠT!
                        .body(event.getPayload())
                        .retrieve()
                        .toBodilessEntity(); // Chỉ cần biết HTTP 2xx thành công, không cần parse body

                // 3a. PHA 3 (Thành công): Chốt sổ
                processor.markAsProcessed(event.getId());
                log.info("Successfully relayed event ID: {}", event.getId());

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