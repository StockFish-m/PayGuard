package com.payguard.engine.controller.payment;

import com.payguard.engine.config.anotation.Idempotent;
import com.payguard.engine.dto.CheckoutRequest;
import com.payguard.engine.dto.CheckoutResponse;
import com.payguard.engine.service.payment.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    @Idempotent(leaseTime = 10) // LỚP PHÒNG NGỰ SỐ 1: Redis chặn spam click
    public ResponseEntity<CheckoutResponse> createPayment(
            HttpServletRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody CheckoutRequest payload) {

        // 1. Rút App ID đã được ApiKeyInterceptor nhét sẵn vào Request
        String appId = (String) request.getAttribute("X-APP-ID");
        if (appId == null) {
            return ResponseEntity.status(401).build(); // Safety check
        }

        // 2. Quăng cho Service xử lý
        CheckoutResponse response = checkoutService.processCheckout(payload, appId, idempotencyKey);

        return ResponseEntity.ok(response);
    }
}