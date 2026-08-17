package com.payguard.engine.statemachine;

import com.payguard.engine.enums.TransactionEvent;
import com.payguard.engine.enums.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransactionStateMachineTest {

    private TransactionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TransactionStateMachine();
    }

    @Test
    void testInitialCreation() {
        TransactionStatus status = stateMachine.transition(null, TransactionEvent.CREATE_TRANSACTION);
        assertEquals(TransactionStatus.PENDING, status);
    }

    @Test
    void testPendingToSuccess() {
        TransactionStatus status = stateMachine.transition(TransactionStatus.PENDING, TransactionEvent.PAYMENT_SUCCESS);
        assertEquals(TransactionStatus.SUCCESS, status);
    }

    @Test
    void testPendingToFailed() {
        TransactionStatus status = stateMachine.transition(TransactionStatus.PENDING, TransactionEvent.PAYMENT_FAILED);
        assertEquals(TransactionStatus.FAILED, status);
    }

    @Test
    void testPendingToAmountMismatch() {
        TransactionStatus status = stateMachine.transition(TransactionStatus.PENDING, TransactionEvent.PAYMENT_AMOUNT_MISMATCH);
        assertEquals(TransactionStatus.AMOUNT_MISMATCH, status);
    }

    @Test
    void testFailedToSuccessReconciliation() {
        TransactionStatus status = stateMachine.transition(TransactionStatus.FAILED, TransactionEvent.PAYMENT_SUCCESS);
        assertEquals(TransactionStatus.SUCCESS, status);
    }

    @Test
    void testSuccessIsTerminalState() {
        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(TransactionStatus.SUCCESS, TransactionEvent.PAYMENT_AMOUNT_MISMATCH)
        );
    }

    @Test
    void testInvalidTransitionThrowsException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(TransactionStatus.SUCCESS, TransactionEvent.PAYMENT_FAILED)
        );
        assertTrue(ex.getMessage().contains("Invalid state transition"));
    }

    @Test
    void testTryTransitionSuccess() {
        Optional<TransactionStatus> result = stateMachine.tryTransition(TransactionStatus.PENDING, TransactionEvent.PAYMENT_SUCCESS);
        assertTrue(result.isPresent());
        assertEquals(TransactionStatus.SUCCESS, result.get());
    }

    @Test
    void testTryTransitionInvalid() {
        Optional<TransactionStatus> result = stateMachine.tryTransition(TransactionStatus.SUCCESS, TransactionEvent.PAYMENT_FAILED);
        assertTrue(result.isEmpty());
    }
}
