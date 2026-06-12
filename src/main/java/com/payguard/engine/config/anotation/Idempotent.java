package com.payguard.engine.config.anotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // Nhãn này chỉ được phép cắm trên đầu Hàm (Method)
@Retention(RetentionPolicy.RUNTIME) // Nhãn có hiệu lực trong suốt lúc ứng dụng đang chạy
public @interface Idempotent {

    // Bạn có thể tùy chỉnh thời gian giữ khóa nếu muốn, mặc định là 10 giây
    long leaseTime() default 10;
}