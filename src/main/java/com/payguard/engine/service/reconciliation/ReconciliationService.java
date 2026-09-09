package com.payguard.engine.service.reconciliation;

import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.provider.dto.ReconciliationTransactionDTO;
import com.payguard.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationProcessor reconciliationProcessor;
    private final TransactionRepository transactionRepository;
    private final PaymentProvider paymentProvider; // Tiêm PaymentProvider thay vì phụ thuộc WebClient/SDK cứng

    // Constructor Injection nhận cả 3 dependency an toàn 100%
    public ReconciliationService(ReconciliationProcessor reconciliationProcessor,
            TransactionRepository transactionRepository,
            PaymentProvider paymentProvider) {
        this.reconciliationProcessor = reconciliationProcessor;
        this.transactionRepository = transactionRepository;
        this.paymentProvider = paymentProvider;
    }

    // Đánh dấu đây là hàm "người gác cổng" để kích hoạt quy trình đối soát
    @Scheduled(cron = "0 59 23 * * *")
    public void runReconciliation() {
        log.info("==> [Reconciliation] Starting active reconciliation process with payment provider [{}]...",
                paymentProvider.getProviderId());

        try {
            // BƯỚC 1: Gọi qua PaymentProvider trừu tượng để lấy danh sách giao dịch
            List<ReconciliationTransactionDTO> transactions = paymentProvider.fetchReconciliationTransactions();

            if (transactions.isEmpty()) {
                log.warn("==> [Reconciliation] No transactions returned from provider or error occurred!");
                return;
            }

            log.info("==> [Reconciliation] Successfully fetched {} live transactions from provider.",
                    transactions.size());

            // BƯỚC 2 & 3: Duyệt qua từng giao dịch để đối chiếu với DB THẬT
            for (ReconciliationTransactionDTO txn : transactions) {
                String orderCode = txn.orderCode();
                long providerAmount = txn.amount();

                // 3. ĐỌC DỮ LIỆU THẬT TỪ DATABASE MYSQL
                Optional<Transaction> dbTransactionOpt = transactionRepository.findById(orderCode);

                if (dbTransactionOpt.isEmpty()) {
                    // Kịch bản A: Không tìm thấy mã đơn này dưới DB của mình
                    log.error("==> [Reconciliation] Order {} DOES NOT EXIST in Database! Action required.", orderCode);
                    continue;
                }

                // Nếu tìm thấy, bốc dữ liệu thật ra
                Transaction dbTxn = dbTransactionOpt.get();
                TransactionStatus dbStatus = dbTxn.getStatus();
                long dbAmount = dbTxn.getAmount();

                // 4. Gọi bộ não xử lý đối soát riêng biệt của bạn - Giữ nguyên không đổi
                reconciliationProcessor.processRow(orderCode, providerAmount, dbStatus, dbAmount);
            }

            log.info("==> [Reconciliation] Reconciliation process completed.");
        } catch (Exception e) {
            log.error("==> [Reconciliation] Critical error during reconciliation: ", e);
        }
    }
}