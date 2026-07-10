package com.payguard.engine.retry;

/**
 * Chiến lược định hình chính sách thử lại (Retry Strategy)
 * dành riêng cho lõi xử lý sự kiện giao dịch thanh toán.
 */
public interface RetryPolicy {

    // Khai báo loại sự kiện mà chính sách này hỗ trợ
    String getSupportedEventType();

    /**
     * Tính toán khoảng thời gian phải chờ (tính bằng giây) cho lần thử tiếp theo.
     *
     * @param retryCount Số lần đã thực hiện thử lại thất bại trước đó
     * @return Số giây cần dãn cách (đã bao gồm độ nhiễu ngẫu nhiên Jitter)
     */
    long calculateNextDelaySeconds(int retryCount);

    /**
     * Quyết định xem sự kiện này có được phép tiếp tục thử lại hay không,
     * hay phải cách ly vào hàng đợi chết (DEAD / Dead Letter Queue).
     *
     * @param currentRetryCount Số lần đã thử lại hiện tại
     * @return true nếu được phép thử tiếp, false nếu phải dừng lại
     */
    boolean shouldRetry(int currentRetryCount);
}