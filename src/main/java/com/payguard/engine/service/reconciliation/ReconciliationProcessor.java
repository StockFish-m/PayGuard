package com.payguard.engine.service.reconciliation;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.event.OutboxWakeupEvent;
import com.payguard.engine.repository.OutboxEventRepository;
import com.payguard.engine.repository.TransactionRepository;
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

    public ReconciliationProcessor(TransactionRepository transactionRepository,
            OutboxEventRepository outboxEventRepository, ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void processRow(String orderCode, long payOsAmount, String dbStatus, long dbAmount) {
        // KỊCH BẢN C: Khớp hoàn toàn
        if ("SUCCESS".equals(dbStatus) && payOsAmount == dbAmount) {
            log.info(
                    "==> [Reconciliation] Order {} is fully matched. status={}, payOSAmount={}, dbAmount={}. Skipping.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KỊCH BẢN D: Cần cập nhật đơn hàng thành SUCCESS (Cứu đơn rớt mạng cho khách)
        if (("PENDING".equals(dbStatus) || "FAILED".equals(dbStatus)) && payOsAmount == dbAmount) {
            log.warn(
                    "==> [Reconciliation] Order {} needs to be updated to SUCCESS. currentStatus={}, payOSAmount={}, dbAmount={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount);

            // 2. HÀNH ĐỘNG THỰC TẾ: Cập nhật dữ liệu sống xuống MySQL
            Transaction txn = transactionRepository.findById(orderCode).orElse(null);
            if (txn != null) {
                txn.setStatus("SUCCESS");
                transactionRepository.save(txn); // Ghi đè trạng thái mới xuống DB

                // Lưu vào bảng Outbox để hệ thống sau xử lý tiếp

                OutboxEvent event = new OutboxEvent();
                event.setAggregateType("TRANSACTION");
                event.setAggregateId(orderCode);
                event.setEventType("PAYMENT_SUCCESS");
                event.setPayload(String.format("{\"orderCode\":\"%s\",\"amount\":%d,\"status\":\"SUCCESS\"}", orderCode,
                        payOsAmount));
                // Lưu xuống DB
                OutboxEvent savedEvent = outboxEventRepository.save(event);

                // PHÁT THANH: Báo cho Worker biết có đơn mới!
                // Luôn tạo ra savedEvent để thao tác, không thao tác trên event.
                eventPublisher.publishEvent(new OutboxWakeupEvent(savedEvent.getId()));
                log.info(
                        "==> [Reconciliation] UPDATED order {} to SUCCESS in Database and saved to Outbox!",
                        orderCode);
            }
            return;
        }

        // KỊCH BẢN E: Cần điều tra thủ công vì số tiền không khớp
        if (payOsAmount != dbAmount) {
            long delta = payOsAmount - dbAmount;
            log.warn(
                    "==> [Reconciliation] Order {} needs manual investigation due to amount mismatch. status={}, payOSAmount={}, dbAmount={}, delta={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount, delta);

            // 3. HÀNH ĐỘNG THỰC TẾ: Đổi trạng thái thành lệch tiền để Admin xử lý sau
            Transaction txn = transactionRepository.findById(orderCode).orElse(null);
            if (txn != null) {
                txn.setStatus("AMOUNT_MISMATCH");
                transactionRepository.save(txn);

                OutboxEvent event = new OutboxEvent();
                event.setAggregateType("TRANSACTION");
                event.setAggregateId(orderCode);
                event.setEventType("PAYMENT_AMOUNT_MISMATCH");
                event.setPayload(String.format("{\"orderCode\":\"%s\",\"amount\":%d,\"status\":\"AMOUNT_MISMATCH\"}",
                        orderCode, payOsAmount));

                // Lưu xuống DB
                OutboxEvent savedEvent = outboxEventRepository.save(event);

                // PHÁT THANH: Báo cho Worker biết có đơn mới!
                // Luôn tạo ra savedEvent để thao tác, không thao tác trên event.
                eventPublisher.publishEvent(new OutboxWakeupEvent(savedEvent.getId()));
                log.warn(
                        "==> [Reconciliation] CHANGED status of order {} to AMOUNT_MISMATCH and saved to Outbox for accountant processing.",
                        orderCode);
            }
            return;
        }

        log.warn(
                "==> [Reconciliation] Order {} has matching amount but unsupported or missing status. status={}, payOSAmount={}, dbAmount={}.",
                orderCode, dbStatus, payOsAmount, dbAmount);
    }
}