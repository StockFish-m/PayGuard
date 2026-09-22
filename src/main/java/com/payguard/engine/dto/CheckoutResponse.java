package com.payguard.engine.dto;

public record CheckoutResponse(
        String orderCode,
        String checkoutUrl,
        String qrCodeString) {
}