package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.global.service.FcmService;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import java.time.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class NotificationDelivery {
  private final ClosingNotificationRepository notifications;
  private final UserRepository users;
  private final FcmService fcm;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void send(Long id) {
    var optional = notifications.lock(id);
    if (optional.isEmpty()) return;
    var n = optional.get();
    if (java.util.Set.of("SENT", "EXPIRED").contains(n.getDeliveryStatus())
        || n.getAttempts() >= 3
        || n.getNextAttemptAt().isAfter(LocalDateTime.now(clock))) return;
    if (n.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(1))) {
      n.setDeliveryStatus("EXPIRED");
      return;
    }
    String token = users.findById(n.getUserId()).map(u -> u.getFcmToken()).orElse(null);
    String status =
        fcm.deliver(
            token,
            n.getTitle(),
            n.getBody(),
            Map.of(
                "notificationId",
                n.getId().toString(),
                "type",
                n.getType(),
                "businessDate",
                n.getBusinessDate().toString(),
                "route",
                "closing"));
    n.setDeliveryStatus(status);
    if (status.equals("SENT")) n.setSentAt(LocalDateTime.now(clock));
    if (status.equals("FAILED")) n.setAttempts(n.getAttempts() + 1);
    n.setNextAttemptAt(LocalDateTime.now(clock).plusMinutes(status.equals("FAILED") ? 5 : 1));
  }
}
