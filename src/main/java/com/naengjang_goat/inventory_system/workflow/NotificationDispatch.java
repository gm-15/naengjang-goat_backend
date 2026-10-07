package com.naengjang_goat.inventory_system.workflow;

import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatch {
  private final NotificationDelivery delivery;
  private final ClosingNotificationRepository notifications;
  private final Clock clock;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void afterCommit(ClosingContracts.NotificationEvent event) {
    send(event.id());
  }

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
  public void retry() {
    for (var n :
        notifications
            .findTop50ByDeliveryStatusNotInAndAttemptsLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                java.util.List.of("SENT", "EXPIRED"), 3, LocalDateTime.now(clock))) send(n.getId());
  }

  private void send(Long id) {
    try {
      delivery.send(id);
    } catch (Exception e) {
      log.warn("Notification dispatch deferred id={}", id);
    }
  }
}
