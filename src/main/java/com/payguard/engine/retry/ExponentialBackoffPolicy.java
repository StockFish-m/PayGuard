package com.payguard.engine.retry;

import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ExponentialBackoffPolicy implements RetryPolicy {

    // Giới hạn chịu lỗi tối đa 6 lần (Chạm ngưỡng ~4.3 tiếng chờ như bạn đã thiết
    // kế)
    private static final int MAX_RETRIES = 6;
    private static final int BASE_MULTIPLIER = 5;

    // Biên độ nhiễu Jitter nhằm phá vỡ "Hiệu ứng bầy đàn" (±20%)
    private static final double JITTER_MIN = 0.8;
    private static final double JITTER_MAX = 1.2;

    @Override
    public String getSupportedEventType() {
        // Mặc định
        return "PAYMENT_SUCCESS";
    }

    @Override
    public long calculateNextDelaySeconds(int retryCount) {
        if (retryCount <= 1) {
            return 5; // Lần đầu tiên lỗi (retryCount = 1) sẽ thử lại sau 5 giây cơ bản
        }

        // 1. Tính toán thời gian chờ cơ sở theo hàm mũ: 5^retryCount
        long baseDelay = (long) Math.pow(BASE_MULTIPLIER, retryCount);

        // 2. Tối ưu chịu tải: Dùng ThreadLocalRandom cô lập luồng để lấy Jitter ngẫu
        // nhiên từ 0.8 đến 1.2
        double jitterFactor = ThreadLocalRandom.current().nextDouble(JITTER_MIN, JITTER_MAX);

        // 3. Chốt hạ thời gian dãn cách cuối cùng
        return (long) (baseDelay * jitterFactor);
    }

    @Override
    public boolean shouldRetry(int currentRetryCount) {
        // Cho phép thử lại tối đa 6 lần (khi retryCount <= 6)
        return currentRetryCount <= MAX_RETRIES;
    }
}