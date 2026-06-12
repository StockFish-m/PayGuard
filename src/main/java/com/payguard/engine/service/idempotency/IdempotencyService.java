package com.payguard.engine.service.idempotency;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private final RedissonClient redissonClient;

    public IdempotencyService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Thử xử lý request một cách an toàn bằng Khóa phân tán
     * 
     * @return true nếu xử lý hợp lệ (không trùng), false nếu là request trùng lặp
     *         bị chặn lại
     */
    public boolean tryProcessRequest(String idempotencyKey) {
        // Tạo một cái khóa dựa trên mã định danh duy nhất của request
        String lockKey = "lock:idempotency:" + idempotencyKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            /*
             * Cố gắng giật khóa (Distributed Lock):
             * - waitTime = 0: Nếu có thằng khác đang giữ khóa này rồi, tôi KHÔNG CHỜ ĐỢI,
             * báo thất bại luôn (chặn trùng ngay lập tức).
             * - leaseTime = 10: Nếu tôi lấy được khóa, tôi sẽ giữ nó trong tối đa 10 giây.
             * Sau 10 giây khóa tự nhả (tránh nghẽn mạch hệ thống).
             */
            boolean isLockAcquired = lock.tryLock(0, 10, TimeUnit.SECONDS);

            if (!isLockAcquired) {
                // Request trùng lặp đến cùng lúc không lấy được khóa -> Bị từ chối thẳng mặt
                log.warn("==> [Idempotency] Detected concurrent DUPLICATE request for key: {}. Blocked!",
                        idempotencyKey);
                return false;
            }

            log.info("==> [Idempotency] Lock acquired SUCCESSFULLY for key: {}. Starting business logic...", idempotencyKey);

            // Giả lập thời gian xử lý thanh toán thực tế tốn 2 giây (để dễ test
            // concurrency)
            Thread.sleep(2000);

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // Xử lý xong xuôi thì phải giải phóng (nhả) khóa ra cho các request hợp lệ tiếp
            // theo
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("==> [Idempotency] Released lock safely for key: {}", idempotencyKey);
            }
        }
    }
}