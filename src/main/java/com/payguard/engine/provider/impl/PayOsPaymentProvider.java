package com.payguard.engine.provider.impl;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.config.PayOsProperties;
import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.provider.dto.PaymentRequest;
import com.payguard.engine.provider.dto.PaymentResponse;
import com.payguard.engine.provider.dto.ReconciliationTransactionDTO;
import com.payguard.engine.repository.TransactionRepository;
import com.payguard.engine.util.PayGuardSecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PayOsPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PayOsPaymentProvider.class);

    // PayOS giới hạn orderCode tối đa là 2^53 - 1 (Number.MAX_SAFE_INTEGER trong Javascript)
    private static final long MAX_PAYOS_ORDER_CODE = 9007199254740991L;
    // Mốc thời gian Custom Epoch: 2026-01-01T00:00:00Z (giúp số mili-giây nhỏ, vừa vặn trong 53-bit)
    private static final long CUSTOM_EPOCH = 1767225600000L;
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final RestClient payOsRestClient;
    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final PayGuardSecurityUtil payGuardSecurityUtil;

    public PayOsPaymentProvider(RestClient payOsRestClient,
                                PayOsProperties payOsProperties,
                                ObjectMapper objectMapper,
                                TransactionRepository transactionRepository,
                                PayGuardSecurityUtil payGuardSecurityUtil) {
        this.payOsRestClient = payOsRestClient;
        this.payOsProperties = payOsProperties;
        this.objectMapper = objectMapper;
        this.transactionRepository = transactionRepository;
        this.payGuardSecurityUtil = payGuardSecurityUtil;
    }

    @Override
    public String getProviderId() {
        return "PAYOS";
    }

    @Override
    public PaymentResponse createPaymentLink(PaymentRequest request) {
        log.info("==> [PayOS] Creating payment link for transaction: {}", request.transactionId());

        try {
            long orderCode = extractOrderCode(request.transactionId());
            long amount = request.amount().longValue();
            String description = request.description() != null && !request.description().isBlank()
                    ? request.description()
                    : "Thanh toan " + orderCode;
            String cancelUrl = request.cancelUrl() != null ? request.cancelUrl() : "";
            String returnUrl = request.returnUrl() != null ? request.returnUrl() : "";

            // Chuỗi dữ liệu ký HMAC theo thứ tự alphabet: amount, cancelUrl, description, orderCode, returnUrl
            String dataToHash = String.format("amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s",
                    amount, cancelUrl, description, orderCode, returnUrl);
            String signature = payGuardSecurityUtil.signHmacSha256(dataToHash, payOsProperties.getChecksumKey());

            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("orderCode", orderCode);
            reqBody.put("amount", amount);
            reqBody.put("description", description);
            reqBody.put("cancelUrl", cancelUrl);
            reqBody.put("returnUrl", returnUrl);
            reqBody.put("signature", signature);

            JsonNode rootNode = payOsRestClient.post()
                    .uri("/v2/payment-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (rootNode == null || !"00".equals(rootNode.path("code").asText())) {
                String errorDesc = rootNode != null ? rootNode.path("desc").asText() : "No response from PayOS";
                log.error("==> [PayOS] Failed to create payment link: {}", errorDesc);
                throw new IllegalStateException("Failed to create PayOS payment link: " + errorDesc);
            }

            JsonNode dataNode = rootNode.path("data");
            String paymentLinkId = dataNode.path("paymentLinkId").asText();
            String checkoutUrl = dataNode.path("checkoutUrl").asText();
            String qrCode = dataNode.path("qrCode").asText();

            log.info("==> [PayOS] Successfully created payment link: {}", checkoutUrl);
            return new PaymentResponse(paymentLinkId, checkoutUrl, qrCode);

        } catch (Exception e) {
            log.error("==> [PayOS] Error while creating payment link: ", e);
            throw new RuntimeException("Error communicating with PayOS: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            String checksumKey = payOsProperties.getChecksumKey();
            if (checksumKey == null || checksumKey.isBlank()) {
                log.error("==> [PayOS] ERROR: PAYOS_CHECKSUM_KEY is not configured!");
                return false;
            }

            JsonNode rootNode = objectMapper.readTree(payload);
            String signatureToVerify = (signature != null && !signature.isBlank())
                    ? signature
                    : rootNode.path("signature").asText();

            JsonNode dataNode = rootNode.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                log.warn("==> [PayOS] Webhook payload missing 'data' field");
                return false;
            }

            // Chuyển JsonNode thành Map và sắp xếp Key A-Z bằng TreeMap
            Map<String, Object> dataMap = objectMapper.convertValue(dataNode, new TypeReference<>() {});
            TreeMap<String, Object> sortedMap = new TreeMap<>(dataMap);

            StringBuilder stringBuilder = new StringBuilder();
            for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                    if (!stringBuilder.isEmpty()) {
                        stringBuilder.append("&");
                    }
                    stringBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            String dataToHash = stringBuilder.toString();
            String generatedSignature = payGuardSecurityUtil.signHmacSha256(dataToHash, checksumKey);

            return generatedSignature.equals(signatureToVerify);

        } catch (Exception e) {
            log.error("==> [PayOS] Critical error while verifying webhook signature: ", e);
            return false;
        }
    }

    @Override
    public ReconciliationTransactionDTO fetchTransaction(String orderCodeStr) {
        long orderCode = extractOrderCode(orderCodeStr);
        try {
            JsonNode root = payOsRestClient.get()
                    .uri("/v2/payment-requests/{orderCode}", orderCode)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null || !"00".equals(root.path("code").asText())) {
                log.warn("==> [PayOS] Order code {} not found or returned error: {}",
                        orderCode, root != null ? root.path("desc").asText() : "null");
                return null;
            }

            JsonNode data = root.path("data");
            String status = data.path("status").asText();
            long amount = data.path("amount").asLong();

            return new ReconciliationTransactionDTO(orderCodeStr, amount, status);

        } catch (Exception e) {
            log.error("==> [PayOS] Error querying PayOS for orderCode {}: {}", orderCode, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ReconciliationTransactionDTO> fetchReconciliationTransactions() {
        // Bước 1: Chỉ lấy các đơn PENDING từ DB nội bộ của PayGuard để đi đối soát
        List<Transaction> pendingTxns = transactionRepository.findByStatus(TransactionStatus.PENDING);

        log.info("==> [PayOS] Starting reconciliation for {} PENDING transactions from DB", pendingTxns.size());
        List<ReconciliationTransactionDTO> result = new ArrayList<>();

        // Bước 2: Vòng lặp truy vấn trạng thái từng đơn từ PayOS API (GET /v2/payment-requests/{orderCode})
        for (Transaction txn : pendingTxns) {
            ReconciliationTransactionDTO dto = fetchTransaction(txn.getOrderCode());
            if (dto != null) {
                result.add(dto);
            }
        }

        log.info("==> [PayOS] Reconciliation completed. Fetched {} valid transaction results from PayOS", result.size());
        return result;
    }

    /**
     * Bóc tách hoặc sinh orderCode số nguyên dương độc nhất <= 2^53 - 1.
     * Tuyệt đối không dùng hashCode() để tránh sinh số âm và va chạm dữ liệu.
     */
    public long extractOrderCode(String transactionId) {
        if (transactionId != null && !transactionId.isBlank()) {
            String digits = transactionId.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    long parsed = Long.parseLong(digits);
                    if (parsed > 0 && parsed <= MAX_PAYOS_ORDER_CODE) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Nếu chuỗi số quá dài vượt quá Long.MAX_VALUE, chuyển sang bộ sinh độc nhất
                }
            }
        }
        // Fallback an toàn: Sử dụng thuật toán Snowflake 53-bit đảm bảo luôn > 0, duy nhất, không va chạm
        return generateUniqueOrderCode();
    }

    /**
     * Sinh mã số nguyên dương duy nhất theo kiến trúc Snowflake rút gọn trong phạm vi 53-bit.
     * Cấu trúc: [41 bit timestamp tương đối] + [12 bit sequence (tối đa 4096 txns/ms)]
     */
    private synchronized long generateUniqueOrderCode() {
        long currentMillis = System.currentTimeMillis();
        long diff = currentMillis - CUSTOM_EPOCH;
        if (diff < 0) {
            diff = currentMillis;
        }

        long seq = SEQUENCE.incrementAndGet() & 0xFFFL; // 12-bit (0 - 4095)
        long code = ((diff << 12) | seq) % MAX_PAYOS_ORDER_CODE;
        return code <= 0 ? 1 : code;
    }
}
