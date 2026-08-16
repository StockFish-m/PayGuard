package com.payguard.engine.controller;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
public class TestOutboxController {

    private final OutboxEventRepository repository;

    public TestOutboxController(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/test/trigger-outbox")
    public String triggerEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("PAYMENT");
        event.setAggregateId(UUID.randomUUID().toString()); // Giả lập ID hóa đơn
        event.setEventType("PAYMENT_SUCCESS");
        // Gói hàng JSON gửi đi
        event.setPayload("{\"orderId\": \"12345\", \"amount\": 100000, \"status\": \"SUCCESS\"}");
        event.setStatus("PENDING");
        event.setCreatedAt(LocalDateTime.now());
        event.setNextRetryAt(LocalDateTime.now()); // Sẵn sàng chạy ngay lập tức
        event.setRetryCount(0);

        repository.save(event); // Cất vào kho

        return "Đã ném 1 Event vào Database thành công! Hãy xem Log của Worker.";
    }
}