package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpeningScheduler {
  private final StoreSettingsRepository settings;
  private final ClosingService closing;
  private final Clock clock;

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
  public void check() {
    var now = LocalDateTime.now(clock);
    for (var s : settings.findAll()) {
      // Include tomorrow so 02:00 opening is notified at 23:00 on the previous day.
      for (int offset = 0; offset <= 1; offset++) {
        LocalDate date = now.toLocalDate().plusDays(offset);
        LocalDateTime target = date.atTime(s.getOpenTime()).minusHours(3);
        // Recover short outages; never send the same day's opening event twice.
        if (!now.isBefore(target) && now.isBefore(date.atTime(s.getOpenTime()))) {
          try {
            closing.prepareOpening(s.getUser().getId(), date);
          } catch (Exception e) {
            log.warn("Opening judgment deferred userId={}", s.getUser().getId());
          }
        }
      }
    }
  }
}
