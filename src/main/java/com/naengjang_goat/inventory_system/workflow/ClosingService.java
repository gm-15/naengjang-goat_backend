package com.naengjang_goat.inventory_system.workflow;

import static com.naengjang_goat.inventory_system.workflow.ClosingContracts.*;

import com.naengjang_goat.inventory_system.inventory.repository.*;
import com.naengjang_goat.inventory_system.pricing.service.PriceTrendService;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClosingService {
  private final InventoryGate gate;
  private final IngredientRepository ingredients;
  private final InventoryBatchRepository batches;
  private final PosReportService reports;
  private final PosUploadRepository uploads;
  private final StoreSettingsRepository settings;
  private final PriceTrendService trends;
  private final ClosingNotificationRepository notifications;
  private final WorkflowJson json;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public Recommendations recommendations(Long userId, LocalDate date) {
    LocalDate today = LocalDate.now(clock);
    var configuration = settings.findByUserId(userId);
    int days =
        configuration
            .map(
                s -> {
                  int d =
                      s.getOrderDay().toJavaDayOfWeek().getValue() - date.getDayOfWeek().getValue();
                  return d <= 0 ? d + 7 : d;
                })
            .orElse(7);
    var latest = uploads.findFirstByUserIdOrderByBusinessDateDescCreatedAtDesc(userId);
    boolean recent =
        latest.isPresent() && !latest.get().getBusinessDate().isBefore(today.minusDays(29));
    List<Item> items = new ArrayList<>();
    for (var ingredient : ingredients.findAllByUserIdWithFetch(userId)) {
      BigDecimal stock = batches.sumUsableQuantity(ingredient.getId(), today);
      BigDecimal daily = reports.dailyAverage(userId, ingredient.getId());
      BigDecimal quantity =
          daily.signum() > 0 && recent
              ? daily.multiply(BigDecimal.valueOf(days)).subtract(stock).max(BigDecimal.ZERO)
              : null;
      LocalDate depletion =
          daily.signum() > 0 && recent
              ? today.plusDays(stock.divide(daily, 0, RoundingMode.CEILING).longValue())
              : null;
      boolean signal = false;
      int coverage = 0;
      String priceReason = "가격 데이터 없음";
      try {
        var trend = trends.getTrend(ingredient.getId(), 30);
        signal = trend.isCurrentBuySignal();
        coverage = trend.getDataCoverage();
        priceReason = trend.getSignalReason();
      } catch (Exception e) {
        log.warn("Price judgment unavailable ingredientId={}", ingredient.getId());
        priceReason = "가격 판단 불가";
      }
      String reason =
          !recent
              ? "최근 POS 판매 자료 미반영"
              : daily.signum() == 0
                  ? "소비 이력 부족"
                  : quantity.signum() > 0 ? "다음 발주일까지 재고 부족 예상" : "다음 발주일까지 재고 충분";
      items.add(
          new Item(
              ingredient.getId(),
              ingredient.getName(),
              ingredient.getBaseUnit(),
              stock,
              daily,
              days,
              quantity,
              depletion,
              quantity != null && quantity.signum() > 0,
              signal,
              coverage,
              priceReason,
              reason));
    }
    return new Recommendations(
        date,
        LocalDateTime.now(clock),
        latest.map(PosUpload::getBusinessDate).orElse(null),
        latest.map(PosUpload::getCreatedAt).orElse(null),
        configuration.isPresent(),
        items);
  }

  @Transactional
  public AlertResult prepareUpload(Long userId, Long uploadId, LocalDate date) {
    return prepare(userId, "UPLOAD:" + uploadId, "UPLOAD", date);
  }

  @Transactional
  public AlertResult prepareOpening(Long userId, LocalDate date) {
    return prepare(userId, "OPENING:" + date, "OPENING", date);
  }

  private AlertResult prepare(Long userId, String key, String type, LocalDate date) {
    gate.lock(userId);
    var previous = notifications.findByUserIdAndEventKey(userId, key);
    if (previous.isPresent()) return view(previous.get());
    var result = recommendations(userId, date);
    long count = result.items().stream().filter(i -> i.stockAlert() || i.buySignal()).count();
    String title = type.equals("UPLOAD") ? "판매 반영 완료 · 발주 판단" : "영업 전 발주 확인";
    String body =
        count > 0
            ? "확인할 재료 " + count + "개. 앱에서 재고와 가격 근거를 확인하세요."
            : result.lastUploadedBusinessDate() == null
                ? "판매 데이터가 없습니다. POS 판매 자료를 반영해주세요."
                : "재고·가격 판단을 갱신했습니다. 앱에서 결과를 확인하세요.";
    ClosingNotification notification = new ClosingNotification();
    notification.setUserId(userId);
    notification.setEventKey(key);
    notification.setType(type);
    notification.setBusinessDate(date);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setRecommendationsJson(json.write(result));
    notification.setCreatedAt(LocalDateTime.now(clock));
    notification.setNextAttemptAt(LocalDateTime.now(clock));
    notifications.saveAndFlush(notification);
    events.publishEvent(new NotificationEvent(notification.getId()));
    return view(notification);
  }

  private AlertResult view(ClosingNotification n) {
    return new AlertResult(n.getId(), n.getType(), n.getBusinessDate(), n.getDeliveryStatus());
  }
}
