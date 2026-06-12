package com.payguard.engine.controller.idempotency;

import com.payguard.engine.config.anotation.Idempotent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class IdempotencyController {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyController.class);

    /**
     * API Thanh toán thực tế của hệ thống
     * Chỉ cần cắm nhãn @Idempotent, mọi bài toán Concurrency biến mất!
     */
    @PostMapping("/checkout")
    @Idempotent(leaseTime = 15) // Tự động bảo vệ trong 15 giây
    public ResponseEntity<String> checkout() throws InterruptedException {
        log.info("==> [Controller] Processing payment and generating movie ticket (Takes 3 seconds)...");
        Thread.sleep(3000);

        return ResponseEntity.ok("Payment successful! Your movie ticket has been created.");
    }
}