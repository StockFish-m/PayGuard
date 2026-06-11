package com.payguard.engine.service.reconciliation;

import com.payguard.engine.entity.Transaction;
import com.payguard.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationProcessor.class);

    // 1. Tiêm Repository vào bộ não để xử lý lưu trữ
    private final TransactionRepository transactionRepository;

    public ReconciliationProcessor(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void processRow(String orderCode, long payOsAmount, String dbStatus, long dbAmount) {

        // KỊCH BẢN C: Khớp hoàn toàn
        if ("SUCCESS".equals(dbStatus) && payOsAmount == dbAmount) {
            log.info("==> [Doi soat] Don hang {} khop hoan toan. status={}, payOSAmount={}, dbAmount={}. Bo qua.",
                    orderCode, dbStatus, payOsAmount, dbAmount);
            return;
        }

        // KỊCH BẢN D: Cần cập nhật đơn hàng thành SUCCESS (Cứu đơn rớt mạng cho khách)
        if (("PENDING".equals(dbStatus) || "FAILED".equals(dbStatus)) && payOsAmount == dbAmount) {
            log.warn(
                    "==> [Doi soat] Don hang {} can cap nhat thanh SUCCESS. currentStatus={}, payOSAmount={}, dbAmount={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount);

            // 2. HÀNH ĐỘNG THỰC TẾ: Cập nhật dữ liệu sống xuống MySQL
            Transaction txn = transactionRepository.findById(orderCode).orElse(null);
            if (txn != null) {
                txn.setStatus("SUCCESS");
                transactionRepository.save(txn); // Ghi đè trạng thái mới xuống DB
                log.info("==> [Doi soat] DA TU DONG CAP NHAT don hang {} thanh SUCCESS duoi Database!", orderCode);

                // 💡 Gợi ý tương lai: Đây chính là nơi bạn sẽ gọi sang EmailService để bắn vé
                // xem phim cho khách!
            }
            return;
        }

        // KỊCH BẢN E: Cần điều tra thủ công vì số tiền không khớp
        if (payOsAmount != dbAmount) {
            long delta = payOsAmount - dbAmount;
            log.warn(
                    "==> [Doi soat] Don hang {} can dieu tra thu cong vi so tien khong khop. status={}, payOSAmount={}, dbAmount={}, delta={}.",
                    orderCode, dbStatus, payOsAmount, dbAmount, delta);

            // 3. HÀNH ĐỘNG THỰC TẾ: Đổi trạng thái thành lệch tiền để Admin xử lý sau
            Transaction txn = transactionRepository.findById(orderCode).orElse(null);
            if (txn != null) {
                txn.setStatus("AMOUNT_MISMATCH");
                transactionRepository.save(txn);
                log.warn("==> [Doi soat] DA CHUYEN trang thai don hang {} thanh AMOUNT_MISMATCH đe cho ke toan xu ly.",
                        orderCode);
            }
            return;
        }

        log.warn(
                "==> [Doi soat] Don hang {} co amount khop nhung status khong ho tro hoac bi thieu. status={}, payOSAmount={}, dbAmount={}.",
                orderCode, dbStatus, payOsAmount, dbAmount);
    }
}