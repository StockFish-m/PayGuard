package com.payguard.engine.processor;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import com.payguard.engine.retry.RetryPolicy;
import com.payguard.engine.retry.RetryPolicyFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxProcessor {

    private final OutboxEventRepository repository;
    private final RetryPolicyFactory policyFactory;

    // Tiêm Repository và Chính sách thử lại vào đây
    public OutboxProcessor(OutboxEventRepository repository, RetryPolicyFactory policyFactory) {
        this.repository = repository;
        this.policyFactory = policyFactory;
    }

    /**
     * PHA 1: Khóa dòng và đánh dấu đang xử lý (Transaction siêu ngắn)
     */
    @Transactional
    public List<OutboxEvent> claimAndMarkProcessing() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = repository.findEventsForProcessing(now);

        for (OutboxEvent event : events) {
            event.setStatus("PROCESSING");
            event.setProcessingAt(now);
        }
        // Nhờ cơ chế Dirty Checking của Hibernate, không cần gọi save(),
        // JPA sẽ tự động cập nhật xuống DB khi hàm này kết thúc (Commit Transaction).
        return events;
    }

    /**
     * PHA 3 (Thành công): Chốt sổ giao dịch
     */
    @Transactional
    public void markAsProcessed(Long eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus("PROCESSED");
            event.setProcessedAt(LocalDateTime.now());
        });
    }

    /**
     * PHA 3 (Thất bại): Tính toán dãn cách hoặc đánh dấu CHẾT
     */
    @Transactional
    public void markAsFailed(Long eventId, String errorMessage) {
        repository.findById(eventId).ifPresent(event -> {
            int currentRetries = event.getRetryCount();
            event.setLastError(errorMessage);

            // LẤY ĐÚNG CHÍNH SÁCH DỰA TRÊN LOẠI SỰ KIỆN
            RetryPolicy policy = policyFactory.getPolicy(event.getEventType());

            if (policy.shouldRetry(currentRetries)) {
                long delay = policy.calculateNextDelaySeconds(currentRetries);
                event.setStatus("FAILED");
                event.setRetryCount(currentRetries + 1);
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
            } else {
                event.setStatus("DEAD");
            }
        });
    }
}