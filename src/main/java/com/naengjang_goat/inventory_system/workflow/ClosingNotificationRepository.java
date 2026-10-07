package com.naengjang_goat.inventory_system.workflow;

import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClosingNotificationRepository extends JpaRepository<ClosingNotification, Long> {
  Optional<ClosingNotification> findByUserIdAndEventKey(Long userId, String key);

  Page<ClosingNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from ClosingNotification n where n.id=:id")
  Optional<ClosingNotification> lock(@Param("id") Long id);

  List<ClosingNotification>
      findTop50ByDeliveryStatusNotInAndAttemptsLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
          java.util.Collection<String> statuses, int attempts, LocalDateTime now);
}
