package com.payguard.engine.enums;

/**
 * Enum representing events that trigger state machine transitions for Transactions in PayGuard.
 */
public enum TransactionEvent {
    CREATE_TRANSACTION,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_AMOUNT_MISMATCH,
    PAYMENT_EXPIRED,
    PAYMENT_CANCELLED,
    PAYMENT_REFUNDED
}
