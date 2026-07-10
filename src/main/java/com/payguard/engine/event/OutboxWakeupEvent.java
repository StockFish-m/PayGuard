package com.payguard.engine.event;

// Sử dụng record của Java để tạo class chứa dữ liệu siêu nhanh gọn
public record OutboxWakeupEvent(Long outboxEventId) {

}