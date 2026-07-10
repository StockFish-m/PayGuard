package com.payguard.engine.retry;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RetryPolicyFactory {

    // Chiếc tủ chứa tất cả chính sách, tra cứu siêu tốc O(1)
    private final Map<String, RetryPolicy> policyRegistry;
    private final RetryPolicy defaultPolicy;

    // Spring sẽ tự động tìm tất cả các Bean implement RetryPolicy và nhét vào List
    // này
    public RetryPolicyFactory(List<RetryPolicy> policies) {
        // Biến List thành Map với Key là tên sự kiện (Ví dụ: "PAYMENT_SUCCESS" ->
        // ExponentialBackoffPolicy)
        this.policyRegistry = policies.stream()
                .collect(Collectors.toMap(RetryPolicy::getSupportedEventType, policy -> policy));

        // Tạo một chính sách mặc định an toàn lỡ khi có sự kiện lạ truyền vào
        this.defaultPolicy = new ExponentialBackoffPolicy();
    }

    // Hàm cung cấp Policy cho OutboxProcessor gọi
    public RetryPolicy getPolicy(String eventType) {
        return policyRegistry.getOrDefault(eventType, defaultPolicy);
    }
}