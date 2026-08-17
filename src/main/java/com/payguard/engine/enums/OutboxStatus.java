package com.payguard.engine.enums;

/**
 * Outbox Event Status Enum for PayGuard System.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED,
    DEAD
}
