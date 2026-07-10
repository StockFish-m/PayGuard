package com.payguard.engine.worker;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.event.OutboxWakeupEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);
    private final OutboxEventRepository outboxEventRepository;

    private final RestClient restClient;
    private final String downstreamWebhookUrl;

    // Tiêm động cấu hình URL và khởi tạo công cụ gọi mạng RestClient
    public OutboxRelayWorker(OutboxEventRepository outboxEventRepository,
            @Value("${payguard.downstream.webhook-url:http://localhost:8081/webhook}") String downstreamWebhookUrl) {
        this.outboxEventRepository = outboxEventRepository;
        this.restClient = RestClient.create();
        this.downstreamWebhookUrl = downstreamWebhookUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEventImmediately(OutboxWakeupEvent wakeupEvent) {
        log.info("==> [Outbox Worker] Nhận báo thức Event ID {}. Bắt đầu gửi ngay...", wakeupEvent.outboxEventId());
        outboxEventRepository.findById(wakeupEvent.outboxEventId()).ifPresent(event -> {
            if ("PENDING".equals(event.getStatus()) || "FAILED".equals(event.getStatus())) {
                sendToDownstreamSystem(event);
            }
        });
    }

    @Scheduled(fixedDelay = 900000)
    public void retryFailedOrStuckEvents() {
        List<OutboxEvent> stuckEvents = outboxEventRepository.findByStatus("PENDING");
        stuckEvents.addAll(outboxEventRepository.findByStatus("FAILED"));
        if (!stuckEvents.isEmpty()) {
            log.info("==> [Outbox Sweeper] Đang thử gửi lại {} sự kiện kẹt...", stuckEvents.size());
            stuckEvents.forEach(this::sendToDownstreamSystem);
        }
    }

    /**
     * Lõi gửi dữ liệu đã được TỔNG QUÁT HÓA. Nó không quan tâm đầu kia là ai.
     */
    private void sendToDownstreamSystem(OutboxEvent event) {
        try {
            log.info("==> [Outbox Network] Đang đẩy tín hiệu tới Client: {}", downstreamWebhookUrl);

            // 🚀 Gọi mạng HTTP POST sang hệ thống Downstream
            restClient.post()
                    .uri(downstreamWebhookUrl)
                    .header("Content-Type", "application/json")
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity(); // Đảm bảo bên kia trả về HTTP 200 OK là coi như thành công

            log.info("==> [Outbox Network] TRUYỀN TIN THÀNH CÔNG! Payload = {}", event.getPayload());

            event.setStatus("PROCESSED");
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);

        } catch (Exception e) {
            log.error("==> [Outbox Network] THẤT BẠI khi gọi sang {}. Lỗi: {}", downstreamWebhookUrl, e.getMessage());
            event.setStatus("FAILED");
            outboxEventRepository.save(event);
        }
    }
}