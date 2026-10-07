package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@Profile("demo")
public class DemoClosingController {
  private final ClosingService closing;
  private final Clock clock;

  @PostMapping("/demo/opening-trigger")
  public ClosingContracts.AlertResult trigger(
      @AuthenticationPrincipal CustomUserDetails user,
      @RequestParam(required = false) LocalDate businessDate) {
    if (!"demo".equals(user.getUsername()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "demo 계정 전용입니다");
    LocalDate date = businessDate != null ? businessDate : LocalDate.now(clock).plusDays(1);
    if (date.isBefore(LocalDate.now(clock)) || date.isAfter(LocalDate.now(clock).plusDays(1)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "오늘 또는 내일만 실행할 수 있습니다");
    return closing.prepareOpening(user.getId(), date);
  }
}
