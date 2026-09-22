package com.payguard.engine.dto;

public record CheckoutRequest(
        Long amount,
        String description,
        String returnUrl,
        String cancelUrl) {
}