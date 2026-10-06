package com.naengjang_goat.inventory_system.workflow;

import static com.naengjang_goat.inventory_system.workflow.PosContracts.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class WorkflowController {
  private final PosUploadService pos;
  private final PosReportService reports;
  private final ClosingService closing;
  private final PosMenuMappingRepository mappings;
  private final ClosingNotificationRepository notifications;
  private final WorkflowJson json;

  @PostMapping(value = "/pos/uploads", consumes = "multipart/form-data")
  public UploadResult upload(
      @AuthenticationPrincipal CustomUserDetails user, @RequestParam("file") MultipartFile file) {
    return pos.upload(user.getId(), file);
  }

  @GetMapping("/pos/menu-mappings")
  public List<PosMenuMapping> mappings(@AuthenticationPrincipal CustomUserDetails user) {
    return mappings.findAllByUserId(user.getId());
  }

  @PutMapping("/pos/menu-mappings/{code}")
  public PosMenuMapping map(
      @AuthenticationPrincipal CustomUserDetails user,
      @PathVariable String code,
      @Valid @RequestBody MappingRequest request) {
    return pos.map(user.getId(), code, request.menuId());
  }

  @GetMapping("/reports/daily")
  public DailyReport report(
      @AuthenticationPrincipal CustomUserDetails user, @RequestParam LocalDate businessDate) {
    return reports.daily(user.getId(), businessDate);
  }

  @GetMapping("/closing/recommendations")
  public ClosingContracts.Recommendations recommendations(
      @AuthenticationPrincipal CustomUserDetails user, @RequestParam LocalDate businessDate) {
    return closing.recommendations(user.getId(), businessDate);
  }

  public record NotificationView(
      Long notificationId,
      String type,
      LocalDate businessDate,
      String title,
      String body,
      String deliveryStatus,
      LocalDateTime createdAt,
      LocalDateTime sentAt,
      ClosingContracts.Recommendations recommendations) {}

  @GetMapping("/closing/notifications/{id}")
  public NotificationView notification(
      @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
    var n =
        notifications
            .findById(id)
            .filter(x -> x.getUserId().equals(user.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
    return new NotificationView(
        n.getId(),
        n.getType(),
        n.getBusinessDate(),
        n.getTitle(),
        n.getBody(),
        n.getDeliveryStatus(),
        n.getCreatedAt(),
        n.getSentAt(),
        json.read(n.getRecommendationsJson(), new TypeReference<>() {}));
  }
}
