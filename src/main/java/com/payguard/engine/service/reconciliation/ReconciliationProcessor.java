package com.payguard.engine.service.reconciliation;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionEvent;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.event.OutboxWakeupEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.statemachine.TransactionStateMachine;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationProcessor.class);

    // 1. Tiêm Repository vào bộ não để xử lý lưu trữ
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    // Tiêm State Machine để quản lý và thẩm định quy tắc chuyển trạng thái
    private final TransactionStateMachine stateMachine;

    public ReconciliationProcessor(TransactionRepository transactionRepository,
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher eventPublisher,
            TransactionStateMachine stateMachine) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public void processRow(String orderCode, long payOsAmount, TransactionStatus dbStatus, long dbAmount) {
        // KỊCH BẢN C: Khớp hoàn toàn
        if (dbStatus == TransactionStatus.SUCCESS && payOsAmount == dbAmount) {
            log.info("==> [Reconciliation] Order {} is fully matched. status={}, payOSAmount={}, dbAmount={}. Skipping.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KỊCH BẢN D: Cần cập nhật đơn hàng thành SUCCESS (Cứu đơn rớt mạng cho khách)
        if ((dbStatus == TransactionStatus.PENDING || dbStatus == TransactionStatus.FAILED) && payOsAmount == dbAmount) {
            log.warn("==> [Reconciliation] Order {} needs to be updated to SUCCESS. currentStatus={}, payOSAmount={}, dbAmount={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount);

            // Dùng State Machine kiểm tra xem trạng thái hiện tại có được phép chuyển sang SUCCESS qua event PAYMENT_SUCCESS không
            if (!stateMachine.canTransition(dbStatus, TransactionEvent.PAYMENT_SUCCESS)) {
                log.warn("==> [Reconciliation] Cannot transition order {} from status {} via event PAYMENT_SUCCESS. Skipping.",
                        orderCode, dbStatus);
                return;
            }

            // Tính toán trạng thái tiếp theo thông qua State Machine
            TransactionStatus newStatus = stateMachine.transition(dbStatus, TransactionEvent.PAYMENT_SUCCESS);

            // 2. HÀNH ĐỘNG THỰC TẾ: Cập nhật dữ liệu sống xuống MySQL
            Transaction txn = transactionRepository.findById(orderCode).orElse(null);
            if (txn != null) {
                txn.setStatus(newStatus);
                transactionRepository.save(txn); // Ghi đè trạng thái mới xuống DB

                // Lưu vào bảng Outbox để hệ thống sau xử lý tiếp
                OutboxEvent event = new OutboxEvent();
                event.setAggregateType("TRANSACTION");
                event.setAggregateId(orderCode);
                event.setEventType(TransactionEvent.PAYMENT_SUCCESS.name());
                event.setPayload(String.format("{\"orderCode\":\"%s\",\"amount\":%d,\"status\":\"%s\"}",
                        orderCode, payOsAmount, newStatus.name()));

                // Lưu xuống DB
                OutboxEvent savedEvent = outboxEventRepository.save(event);

                // PHÁT THANH: Báo cho Worker biết có đơn mới!
                // Luôn tạo ra savedEvent để thao tác, không thao tác trên event.
                eventPublisher.publishEvent(new OutboxWakeupEvent(savedEvent.getId()));
                log.info("==> [Reconciliation] UPDATED order {} to {} in Database and saved to Outbox!",
                        orderCode, newStatus);
            }
            return;
        }

        // KỊCH BẢN E: Cần điều tra thủ công vì số tiền không khớp
        if (payOsAmount != dbAmount) {
            long delta = payOsAmount - dbAmount;
            log.warn("==> [Reconciliation] Order {} needs manual investigation due to amount mismatch. status={}, payOSAmount={}, dbAmount={}, delta={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount, delta);

            // Dùng State Machine kiểm tra xem trạng thái hiện tại có được phép chuyển sang AMOUNT_MISMATCH qua event PAYMENT_AMOUNT_MISMATCH không
            if (!stateMachine.canTransition(dbStatus, TransactionEvent.PAYMENT_AMOUNT_MISMATCH)) {
                log.warn("==> [Reconciliation] Cannot transition order {} from status {} via event PAYMENT_AMOUNT_MISMATCH. Skipping.",
                        orderCode, dbStatus);
                return;
            }

            // Tính toán trạng thái tiếp theo thông qua State Machine
            TransactionStatus newStatus = stateMachine.transition(dbStatus, TransactionEvent.PAYMENT_AMOUNT_MISMATCH);

            // 3. HÀNH ĐỘNG THỰC TẾ: Đổi trạng thái thành lệch tiền để Admin xử lý sau
            Transaction txn = transactionRepository.findById(orderCode).orElse(null);
            if (txn != null) {
                txn.setStatus(newStatus);
                transactionRepository.save(txn);

                OutboxEvent event = new OutboxEvent();
                event.setAggregateType("TRANSACTION");
                event.setAggregateId(orderCode);
                event.setEventType(TransactionEvent.PAYMENT_AMOUNT_MISMATCH.name());
                event.setPayload(String.format("{\"orderCode\":\"%s\",\"amount\":%d,\"status\":\"%s\"}",
                        orderCode, payOsAmount, newStatus.name()));

                // Lưu xuống DB
                OutboxEvent savedEvent = outboxEventRepository.save(event);

                // PHÁT THANH: Báo cho Worker biết có đơn mới!
                // Luôn tạo ra savedEvent để thao tác, không thao tác trên event.
                eventPublisher.publishEvent(new OutboxWakeupEvent(savedEvent.getId()));
                log.warn("==> [Reconciliation] CHANGED status of order {} to {} and saved to Outbox for accountant processing.",
                        orderCode, newStatus);
            }
            return;
        }

        log.warn("==> [Reconciliation] Order {} has matching amount but unsupported or missing status. status={}, payOSAmount={}, dbAmount={}.",
                orderCode, dbStatus, payOsAmount, dbAmount);
    }
}