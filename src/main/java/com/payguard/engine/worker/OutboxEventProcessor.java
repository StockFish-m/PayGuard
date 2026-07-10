package com.payguard.engine.worker;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);
    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventProcessor(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Xử lý gửi Event ra ngoài và cập nhật trạng thái trong một Transaction mới
     * riêng biệt.
     * Sử dụng Propagation.REQUIRES_NEW để đảm bảo cập nhật được flush xuống DB ngay
     * cả khi
     * được gọi sau khi transaction trước đó đã commit (phase = AFTER_COMMIT).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(Long eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            if ("PENDING".equals(event.getStatus()) || "FAILED".equals(event.getStatus())) {
                sendToDownstreamSystem(event);
            }
        });
    }

    private void sendToDownstreamSystem(OutboxEvent event) {
        try {
            // Giả lập gọi API truyền tải thông điệp thành công:
            log.info("==> [Outbox Network] Đã truyền tin cho hệ thống đích: Payload = {}", event.getPayload());

            event.setStatus("SENT");
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);

        } catch (Exception e) {
            log.error("==> [Outbox Network] Đứt cáp/Lỗi kết nối khi gửi Event {}: {}", event.getId(), e.getMessage());
            event.setStatus("FAILED");
            outboxEventRepository.save(event);
        }
    }
}
