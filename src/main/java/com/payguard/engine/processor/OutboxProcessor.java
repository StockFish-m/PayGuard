package com.payguard.engine.processor;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.enums.OutboxStatus;
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

    public OutboxProcessor(OutboxEventRepository repository, RetryPolicyFactory policyFactory) {
        this.repository = repository;
        this.policyFactory = policyFactory;
    }

    @Transactional
    public List<OutboxEvent> claimAndMarkProcessing() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = repository.findEventsForProcessing(now);

        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setProcessingAt(now);
        }
        return events;
    }

    @Transactional
    public void markAsProcessed(Long eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
        });
    }

    @Transactional
    public void markAsFailed(Long eventId, String errorMessage) {
        repository.findById(eventId).ifPresent(event -> {
            int nextRetryCount = event.getRetryCount() + 1;
            event.setLastError(errorMessage);

            RetryPolicy policy = policyFactory.getPolicy(event.getEventType());

            if (policy.shouldRetry(nextRetryCount)) {
                long delay = policy.calculateNextDelaySeconds(nextRetryCount);
                event.setStatus(OutboxStatus.FAILED);
                event.setRetryCount(nextRetryCount);
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
            } else {
                event.setStatus(OutboxStatus.DEAD);
            }
        });
    }
}