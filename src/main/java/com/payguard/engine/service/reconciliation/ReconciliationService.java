package com.payguard.engine.service.reconciliation;

import com.payguard.engine.dto.PayOsResponseDTO;
import com.payguard.engine.dto.PayOsTransactionDTO;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationProcessor reconciliationProcessor;
    private final TransactionRepository transactionRepository;
    private final WebClient payOsWebClient; // Tiêm vũ khí đặc chủng vào đây

    // Constructor Injection nhận cả 3 dependency an toàn 100%
    public ReconciliationService(ReconciliationProcessor reconciliationProcessor,
            TransactionRepository transactionRepository,
            WebClient payOsWebClient) {
        this.reconciliationProcessor = reconciliationProcessor;
        this.transactionRepository = transactionRepository;
        this.payOsWebClient = payOsWebClient;
    }

    // Đánh dấu đây là hàm "người gác cổng" để kích hoạt quy trình
    @Scheduled(cron = "0 59 23 * * *")
    public void runReconciliation() {
        log.info("==> [Reconciliation] Starting active reconciliation process with real payOS API...");

        try {
            // BƯỚC 1: Gọi qua API thật của payOS ra Internet thông qua WebClient đặc chủng
            PayOsResponseDTO response = payOsWebClient.get()
                    .uri("/v2/payment-requests")
                    .retrieve()
                    .bodyToMono(PayOsResponseDTO.class)
                    .block(); // Ép chạy tuần tự theo kiểu đồng bộ

            if (response == null || !"00".equals(response.getCode()) || response.getData() == null) {
                log.error("==> [Reconciliation] Failed to fetch data from payOS or API returned an error!");
                return;
            }

            List<PayOsTransactionDTO> payOsTransactions = response.getData();
            log.info("==> [Reconciliation] Successfully fetched {} live transactions from payOS.",
                    payOsTransactions.size());

            // BƯỚC 2 & 3: Duyệt qua từng giao dịch của payOS để đối chiếu với DB THẬT (Áp
            // dụng Cách A)
            for (PayOsTransactionDTO payOsTxn : payOsTransactions) {
                // Ép kiểu an toàn từ Long của DTO sang String khóa chính của DB bạn
                // String orderCode = String.valueOf(payOsTxn.getOrderCode());
                String orderCode = "TXN-00" + String.valueOf(payOsTxn.getOrderCode());
                long payOsAmount = payOsTxn.getAmount();

                // 3. ĐỌC DỮ LIỆU THẬT TỪ DATABASE MYSQL
                Optional<Transaction> dbTransactionOpt = transactionRepository.findById(orderCode);

                if (dbTransactionOpt.isEmpty()) {
                    // Kịch bản A: Không tìm thấy mã đơn này dưới DB của mình
                    log.error("==> [Reconciliation] Order {} DOES NOT EXIST in Database! Action required.", orderCode);
                    continue;
                }

                // Nếu tìm thấy, bốc dữ liệu thật ra
                Transaction dbTxn = dbTransactionOpt.get();
                String dbStatus = dbTxn.getStatus();
                long dbAmount = dbTxn.getAmount();

                // 4. Gọi bộ não xử lý đối soát riêng biệt của bạn - Giữ nguyên không đổi
                reconciliationProcessor.processRow(orderCode, payOsAmount, dbStatus, dbAmount);
            }

            log.info("==> [Reconciliation] Reconciliation process completed.");
        } catch (Exception e) {
            log.error("==> [Reconciliation] Critical error during internet connectivity with payOS: ", e);
        }
    }
}