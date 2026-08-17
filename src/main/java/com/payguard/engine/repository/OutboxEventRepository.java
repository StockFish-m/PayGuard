package com.payguard.engine.repository;

import com.payguard.engine.entity.OutboxEvent;
import com.payguard.engine.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  // TỐI ƯU CHỊU TẢI: Lấy 100 dòng PENDING/FAILED, bỏ qua các dòng đang bị
  // Thread khác giữ
  @Query(value = """
      SELECT * FROM outbox_events
      WHERE status IN ('PENDING', 'FAILED')
        AND (next_retry_at <= :now OR next_retry_at IS NULL)
      ORDER BY created_at ASC
      LIMIT 100
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<OutboxEvent> findEventsForProcessing(@Param("now") LocalDateTime now);

  List<OutboxEvent> findByStatus(OutboxStatus status);
}