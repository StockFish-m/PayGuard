package com.payguard.engine.service.payment;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionEvent;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.event.OutboxWakeupEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.statemachine.TransactionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionStateMachine stateMachine;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentWebhookService(TransactionRepository transactionRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 TransactionStateMachine stateMachine,
                                 ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Xử lý webhook thời gian thực từ Cổng thanh toán (PayOS).
     * Được bọc trong 1 Transaction duy nhất: Cập nhật Transaction DB + ghi nhận OutboxEvent.
     */
    @Transactional
    public boolean processWebhookPayment(String dbOrderCode, String payOsStatus, long payOsAmount) {
        Transaction dbTxn = transactionRepository.findById(dbOrderCode).orElse(null);
        if (dbTxn == null) {
            log.error("==> [Webhook] Payment received but order {} does not exist in Database!", dbOrderCode);
            return false;
        }

        TransactionStatus currentStatus = dbTxn.getStatus();

        // 1. Phân loại TransactionEvent dựa trên status từ cổng thanh toán PayOS
        TransactionEvent event = switch (payOsStatus) {
            case "PAID" -> payOsAmount == dbTxn.getAmount()
                    ? TransactionEvent.PAYMENT_SUCCESS
                    : TransactionEvent.PAYMENT_AMOUNT_MISMATCH;
            case "CANCELLED" -> TransactionEvent.PAYMENT_CANCELLED;
            case "FAILED" -> TransactionEvent.PAYMENT_FAILED;
            case "EXPIRED" -> TransactionEvent.PAYMENT_EXPIRED;
            default -> {
                log.warn("==> [Webhook] Unsupported or unhandled PayOS status '{}' for order {}", payOsStatus, dbOrderCode);
                yield null;
            }
        };

        if (event == null) {
            return false;
        }

        // 2. Kiểm tra State Machine xem có cho phép chuyển trạng thái không
        if (!stateMachine.canTransition(currentStatus, event)) {
            log.warn("==> [Webhook] Cannot transition order {} from status {} via event {}. (Order might be already processed or in terminal state)",
                    dbOrderCode, currentStatus, event);
            return false;
        }

        // 3. Thực hiện chuyển trạng thái thông qua State Machine
        TransactionStatus newStatus = stateMachine.transition(currentStatus, event);
        dbTxn.setStatus(newStatus);
        transactionRepository.save(dbTxn);
        log.info("==> [Webhook] Order {} transitioned: {} -> {} (event: {})",
                dbOrderCode, currentStatus, newStatus, event);

        // 4. Ghi OutboxEvent để Worker relay webhook về merchant downstream
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAppId(dbTxn.getAppId());
        outboxEvent.setAggregateType("TRANSACTION");
        outboxEvent.setAggregateId(dbOrderCode);
        outboxEvent.setEventType(event.name());
        outboxEvent.setPayload(String.format("{\"orderCode\":\"%s\",\"amount\":%d,\"status\":\"%s\"}",
                dbOrderCode, payOsAmount, newStatus.name()));

        OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);

        // 5. Đánh thức OutboxRelayWorker xử lý ngay lập tức
        eventPublisher.publishEvent(new OutboxWakeupEvent(savedEvent.getId()));
        log.info("==> [Webhook] Successfully saved OutboxEvent ID {} for order {}", savedEvent.getId(), dbOrderCode);
        return true;
    }
}
