package com.payguard.engine.service.reconciliation;

import com.payguard.engine.entity.Transaction;
import com.payguard.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationProcessor reconciliationProcessor;
    private final TransactionRepository transactionRepository; // 1. Khai báo đường ống DB thật

    // 2. Tiêm cả Processor và Repository vào qua Constructor
    public ReconciliationService(ReconciliationProcessor reconciliationProcessor,
            TransactionRepository transactionRepository) {
        this.reconciliationProcessor = reconciliationProcessor;
        this.transactionRepository = transactionRepository;
    }

    public void runReconciliation() {
        log.info("==> [Reconciliation] Starting active reconciliation process with payOS...");

        // BƯỚC 1: Gọi qua API của payOS để lấy dữ liệu (Tạm thời giữ giả lập payOS để
        // xử lý xong DB trước)
        List<Map<String, Object>> payOsTransactions = fetchTransactionsFromPayOs();

        // BƯỚC 2 & 3: Duyệt qua từng giao dịch của payOS để đối chiếu với DB THẬT
        for (Map<String, Object> payOsTxn : payOsTransactions) {
            String orderCode = (String) payOsTxn.get("orderCode");
            long payOsAmount = (long) payOsTxn.get("amount");

            // 3. ĐỌC DỮ LIỆU THẬT TỪ DATABASE MYSQL
            Optional<Transaction> dbTransactionOpt = transactionRepository.findById(orderCode);

            if (dbTransactionOpt.isEmpty()) {
                // Kịch bản A: Không tìm thấy mã đơn này dưới DB của mình
                log.error("==> [Reconciliation] Order {} DOES NOT EXIST in Database! Action required.", orderCode);
                continue; // Bỏ qua dòng này, đi check dòng tiếp theo
            }

            // Nếu tìm thấy, bốc dữ liệu thật ra
            Transaction dbTxn = dbTransactionOpt.get();
            String dbStatus = dbTxn.getStatus();
            long dbAmount = dbTxn.getAmount();

            // 4. Gọi bộ não xử lý đối soát của Quân
            reconciliationProcessor.processRow(orderCode, payOsAmount, dbStatus, dbAmount);
        }

        log.info("==> [Reconciliation] Reconciliation process completed.");
    }

    // --- Giữ lại hàm giả lập payOS tạm thời, các hàm giả lập DB cũ đã bị XÓA BỎ
    // ---
    private List<Map<String, Object>> fetchTransactionsFromPayOs() {
        return List.of(
                Map.of("orderCode", "TXN-001", "amount", 150000L),
                Map.of("orderCode", "TXN-002", "amount", 150000L),
                Map.of("orderCode", "TXN-003", "amount", 300000L));
    }
}