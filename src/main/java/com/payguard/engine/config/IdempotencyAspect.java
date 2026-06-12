package com.payguard.engine.config;

import com.payguard.engine.config.anotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);
    private final RedissonClient redissonClient;

    public IdempotencyAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // Đánh chặn tất cả các hàm có cắm nhãn @Idempotent
    @Around("@annotation(idempotentAnnotation)")
    public Object interceptRequest(ProceedingJoinPoint joinPoint, Idempotent idempotentAnnotation) throws Throwable {

        // 1. Tự động bốc Request từ HTTP lên để lấy Header "X-Idempotency-Key"
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed(); // Nếu không phải HTTP request thì cho đi qua
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader("X-Idempotency-Key");

        // Nếu client không gửi Key gác cổng lên, ném lỗi từ chối xử lý
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Missing mandatory Header X-Idempotency-Key!");
        }

        // 2. Tiến hành giật khóa phân tán dựa trên Idempotency Key
        String lockKey = "lock:idempotency:" + idempotencyKey;
        RLock lock = redissonClient.getLock(lockKey);

        long leaseTime = idempotentAnnotation.leaseTime();

        // Cố gắng giật khóa, chờ 0 giây, giữ khóa trong `leaseTime` giây
        boolean isLockAcquired = lock.tryLock(0, leaseTime, TimeUnit.SECONDS);

        if (!isLockAcquired) {
            log.warn("==> [AOP Interceptor] Blocked concurrent duplicate request for key: {}", idempotencyKey);
            throw new RuntimeException("Request is being processed, please do not click repeatedly!");
        }

        try {
            log.info("==> [AOP Interceptor] Lock acquired SUCCESSFULLY for key: {}. Allowing execution to enter Controller.",
                    idempotencyKey);

            // 3. Lệnh này kích hoạt cho phép code chạy vào hàm trong Controller thực tế của
            // bạn
            return joinPoint.proceed();

        } finally {
            // 4. Cho dù hàm chạy thành công hay bị lỗi, đi qua đây đều phải nhả khóa an
            // toàn
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("==> [AOP Interceptor] Automatically released lock for key: {}", idempotencyKey);
            }
        }
    }
}
