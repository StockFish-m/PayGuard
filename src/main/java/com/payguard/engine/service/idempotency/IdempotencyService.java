package com.payguard.engine.service.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class IdempotencyService {

    // Tạo Logger để ghi log hệ thống chuẩn SE (không dùng System.out.println)
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final StringRedisTemplate redisTemplate;

    // Dependency Injection thông qua Constructor
    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Kiểm tra chốt chặn Idempotency bằng Redis
     * 
     * @param key Chuỗi duy nhất gửi từ Header (ví dụ: REQ-GODFATHER-999)
     * @return true nếu đây là request đầu tiên và hợp lệ
     * @throws RuntimeException nếu request bị trùng lặp
     */
    public boolean validateRequest(String key) {
        // Khóa sẽ tự động biến mất sau 15 phút để giải phóng bộ nhớ RAM cho Redis
        Duration timeout = Duration.ofMinutes(15);

        // Thực hiện hành động Nguyên tử (Atomic Operation): Kiểm tra và đặt khóa cùng
        // một lúc
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(key, "IN_PROGRESS", timeout);

        // Chuyển đổi an toàn từ Boolean object sang primitive boolean để tránh
        // NullPointerException
        boolean success = (isFirstRequest != null && isFirstRequest);

        if (success) {
            // Trường hợp TRUE: Bạn là người đến đầu tiên
            log.info("==> [Idempotency] Khóa thành công! Khởi tạo tiến trình xử lý cho key: {}", key);
            return true;
        } else {
            // Trường hợp FALSE: Key đã tồn tại trên Redis, chứng tỏ đang bị click đúp hoặc
            // spam
            log.warn("==> [Idempotency] Phát hiện Request trùng lặp! Chặn đứng key: {}", key);

            // Ném ra ngoại lệ để Spring Boot tự động chặn đứng luồng chạy và báo về cho
            // khách hàng
            throw new RuntimeException("Giao dịch của bạn đang được xử lý, vui lòng không ấn lại!");
        }
    }
}
