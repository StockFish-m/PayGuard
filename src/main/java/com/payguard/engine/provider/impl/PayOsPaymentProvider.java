package com.payguard.engine.provider.impl;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.payguard.engine.config.PayOsProperties;
import com.payguard.engine.dto.PayOsResponseDTO;
import com.payguard.engine.dto.PayOsTransactionDTO;
import com.payguard.engine.provider.PaymentProvider;
import com.payguard.engine.provider.dto.PaymentRequest;
import com.payguard.engine.provider.dto.PaymentResponse;
import com.payguard.engine.provider.dto.ReconciliationTransactionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PayOsPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PayOsPaymentProvider.class);

    private final RestClient payOsRestClient;
    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;

    public PayOsPaymentProvider(RestClient payOsRestClient,
                               PayOsProperties payOsProperties,
                               ObjectMapper objectMapper) {
        this.payOsRestClient = payOsRestClient;
        this.payOsProperties = payOsProperties;
        this.objectMapper = objectMapper;
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
            String signature = hmacSha256(dataToHash, payOsProperties.getChecksumKey());

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
            String generatedSignature = hmacSha256(dataToHash, checksumKey);

            return generatedSignature.equals(signatureToVerify);

        } catch (Exception e) {
            log.error("==> [PayOS] Critical error while verifying webhook signature: ", e);
            return false;
        }
    }

    @Override
    public List<ReconciliationTransactionDTO> fetchReconciliationTransactions() {
        log.info("==> [PayOS] Fetching live reconciliation transactions via RestClient...");

        try {
            PayOsResponseDTO response = payOsRestClient.get()
                    .uri("/v2/payment-requests")
                    .retrieve()
                    .body(PayOsResponseDTO.class);

            if (response == null || !"00".equals(response.getCode()) || response.getData() == null) {
                log.error("==> [PayOS] Failed to fetch reconciliation data or API returned error: {}",
                        response != null ? response.getDesc() : "null response");
                return Collections.emptyList();
            }

            List<PayOsTransactionDTO> payOsTransactions = response.getData();
            log.info("==> [PayOS] Received {} transactions from PayOS", payOsTransactions.size());

            List<ReconciliationTransactionDTO> result = new ArrayList<>();
            for (PayOsTransactionDTO txn : payOsTransactions) {
                String orderCode = "TXN-00" + txn.getOrderCode();
                result.add(new ReconciliationTransactionDTO(orderCode, txn.getAmount(), txn.getStatus()));
            }

            return result;

        } catch (Exception e) {
            log.error("==> [PayOS] Error while fetching reconciliation transactions: ", e);
            return Collections.emptyList();
        }
    }

    private long extractOrderCode(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return System.currentTimeMillis();
        }
        String digits = transactionId.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return Math.abs((long) transactionId.hashCode());
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return Math.abs((long) transactionId.hashCode());
        }
    }

    private String hmacSha256(String data, String key) throws Exception {
        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256HMAC.init(secretKey);

        byte[] hashBytes = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
