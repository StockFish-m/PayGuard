package com.payguard.engine.service.payment;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.event.OutboxWakeupEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.statemachine.TransactionStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {

    private TransactionRepository transactionRepository;
    private OutboxEventRepository outboxEventRepository;
    private TransactionStateMachine stateMachine;
    private ApplicationEventPublisher eventPublisher;
    private PaymentWebhookService webhookService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        stateMachine = new TransactionStateMachine();
        eventPublisher = mock(ApplicationEventPublisher.class);

        webhookService = new PaymentWebhookService(
                transactionRepository,
                outboxEventRepository,
                stateMachine,
                eventPublisher
        );
    }

    @Test
    @DisplayName("processWebhookPayment with status PAID and matching amount transitions to SUCCESS")
    void testPaid_MatchingAmount_Success() {
        Transaction txn = new Transaction("TXN-00123", 50000L, TransactionStatus.PENDING, "ASTRACINE");
        when(transactionRepository.findById("TXN-00123")).thenReturn(Optional.of(txn));

        OutboxEvent savedOutbox = new OutboxEvent();
        savedOutbox.setId(101L);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedOutbox);

        boolean result = webhookService.processWebhookPayment("TXN-00123", "PAID", 50000L);

        assertTrue(result);
        assertEquals(TransactionStatus.SUCCESS, txn.getStatus());
        verify(transactionRepository, times(1)).save(txn);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertEquals("ASTRACINE", event.getAppId());
        assertEquals("PAYMENT_SUCCESS", event.getEventType());
        assertEquals("TXN-00123", event.getAggregateId());

        verify(eventPublisher, times(1)).publishEvent(any(OutboxWakeupEvent.class));
    }

    @Test
    @DisplayName("processWebhookPayment with status PAID and mismatched amount transitions to AMOUNT_MISMATCH")
    void testPaid_MismatchedAmount_AmountMismatch() {
        Transaction txn = new Transaction("TXN-00123", 50000L, TransactionStatus.PENDING, "ASTRACINE");
        when(transactionRepository.findById("TXN-00123")).thenReturn(Optional.of(txn));

        OutboxEvent savedOutbox = new OutboxEvent();
        savedOutbox.setId(102L);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedOutbox);

        boolean result = webhookService.processWebhookPayment("TXN-00123", "PAID", 30000L);

        assertTrue(result);
        assertEquals(TransactionStatus.AMOUNT_MISMATCH, txn.getStatus());
        verify(transactionRepository, times(1)).save(txn);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
        assertEquals("PAYMENT_AMOUNT_MISMATCH", outboxCaptor.getValue().getEventType());
    }

    @Test
    @DisplayName("processWebhookPayment with status CANCELLED transitions to FAILED")
    void testCancelled_TransitionsToFailed() {
        Transaction txn = new Transaction("TXN-00123", 50000L, TransactionStatus.PENDING, "ASTRACINE");
        when(transactionRepository.findById("TXN-00123")).thenReturn(Optional.of(txn));

        OutboxEvent savedOutbox = new OutboxEvent();
        savedOutbox.setId(103L);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedOutbox);

        boolean result = webhookService.processWebhookPayment("TXN-00123", "CANCELLED", 50000L);

        assertTrue(result);
        assertEquals(TransactionStatus.FAILED, txn.getStatus());
        verify(transactionRepository, times(1)).save(txn);
    }

    @Test
    @DisplayName("processWebhookPayment ignores duplicate webhook when order is already SUCCESS")
    void testAlreadySuccess_IgnoredGracefully() {
        Transaction txn = new Transaction("TXN-00123", 50000L, TransactionStatus.SUCCESS, "ASTRACINE");
        when(transactionRepository.findById("TXN-00123")).thenReturn(Optional.of(txn));

        boolean result = webhookService.processWebhookPayment("TXN-00123", "PAID", 50000L);

        assertFalse(result);
        assertEquals(TransactionStatus.SUCCESS, txn.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("processWebhookPayment returns false when transaction not found in DB")
    void testNotFound_ReturnsFalse() {
        when(transactionRepository.findById("TXN-404")).thenReturn(Optional.empty());

        boolean result = webhookService.processWebhookPayment("TXN-404", "PAID", 50000L);

        assertFalse(result);
        verify(transactionRepository, never()).save(any());
    }
}
