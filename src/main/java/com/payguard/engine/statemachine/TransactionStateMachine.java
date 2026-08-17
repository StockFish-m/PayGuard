package com.payguard.engine.statemachine;

import com.payguard.engine.enums.TransactionEvent;
import com.payguard.engine.enums.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * State Machine for managing valid Transaction state transitions in PayGuard.
 */
@Component
public class TransactionStateMachine {

    private final Map<TransactionStatus, Map<TransactionEvent, TransactionStatus>> transitions = new EnumMap<>(TransactionStatus.class);

    public TransactionStateMachine() {
        initTransitions();
    }

    private void initTransitions() {
        // --- Transitions from PENDING ---
        Map<TransactionEvent, TransactionStatus> pendingTransitions = new EnumMap<>(TransactionEvent.class);
        pendingTransitions.put(TransactionEvent.PAYMENT_SUCCESS, TransactionStatus.SUCCESS);
        pendingTransitions.put(TransactionEvent.PAYMENT_FAILED, TransactionStatus.FAILED);
        pendingTransitions.put(TransactionEvent.PAYMENT_AMOUNT_MISMATCH, TransactionStatus.AMOUNT_MISMATCH);
        pendingTransitions.put(TransactionEvent.PAYMENT_EXPIRED, TransactionStatus.FAILED);
        pendingTransitions.put(TransactionEvent.PAYMENT_CANCELLED, TransactionStatus.FAILED);
        transitions.put(TransactionStatus.PENDING, pendingTransitions);

        // --- Transitions from FAILED ---
        Map<TransactionEvent, TransactionStatus> failedTransitions = new EnumMap<>(TransactionEvent.class);
        // Allow reconciliation recovery from network drop or false failure report (cứu đơn rớt mạng)
        failedTransitions.put(TransactionEvent.PAYMENT_SUCCESS, TransactionStatus.SUCCESS);
        transitions.put(TransactionStatus.FAILED, failedTransitions);

        // --- Transitions from SUCCESS ---
        // SUCCESS is a Terminal State - no automatic status modification allowed post-fulfillment

        // --- Transitions from AMOUNT_MISMATCH ---
        Map<TransactionEvent, TransactionStatus> mismatchTransitions = new EnumMap<>(TransactionEvent.class);
        // Manual accountant / admin resolution
        mismatchTransitions.put(TransactionEvent.PAYMENT_SUCCESS, TransactionStatus.SUCCESS);
        mismatchTransitions.put(TransactionEvent.PAYMENT_FAILED, TransactionStatus.FAILED);
        transitions.put(TransactionStatus.AMOUNT_MISMATCH, mismatchTransitions);
    }

    /**
     * Calculates the next TransactionStatus given the current status and incoming event.
     *
     * @param currentStatus Current status of the transaction (null if initial creation)
     * @param event Incoming transaction event
     * @return Next status if transition is valid
     * @throws IllegalStateException if the transition is invalid
     */
    public TransactionStatus transition(TransactionStatus currentStatus, TransactionEvent event) {
        // Initial creation
        if (currentStatus == null) {
            if (event == TransactionEvent.CREATE_TRANSACTION) {
                return TransactionStatus.PENDING;
            }
            throw new IllegalStateException("Initial transaction creation must use CREATE_TRANSACTION event, got: " + event);
        }

        Map<TransactionEvent, TransactionStatus> validEvents = transitions.get(currentStatus);
        if (validEvents != null && validEvents.containsKey(event)) {
            return validEvents.get(event);
        }

        throw new IllegalStateException(
                String.format("Invalid state transition from [%s] via event [%s]", currentStatus, event)
        );
    }

    /**
     * Checks whether a transition is allowed from currentStatus via event.
     *
     * @param currentStatus Current status of the transaction
     * @param event Incoming transaction event
     * @return true if allowed, false otherwise
     */
    public boolean canTransition(TransactionStatus currentStatus, TransactionEvent event) {
        if (currentStatus == null) {
            return event == TransactionEvent.CREATE_TRANSACTION;
        }
        Map<TransactionEvent, TransactionStatus> validEvents = transitions.get(currentStatus);
        return validEvents != null && validEvents.containsKey(event);
    }

    /**
     * Attempts transition and returns Optional of next status instead of throwing exception.
     *
     * @param currentStatus Current status of the transaction
     * @param event Incoming transaction event
     * @return Optional containing next status if valid, empty otherwise
     */
    public Optional<TransactionStatus> tryTransition(TransactionStatus currentStatus, TransactionEvent event) {
        if (canTransition(currentStatus, event)) {
            return Optional.of(transition(currentStatus, event));
        }
        return Optional.empty();
    }
}
