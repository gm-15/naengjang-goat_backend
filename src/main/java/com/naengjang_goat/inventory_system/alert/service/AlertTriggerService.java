package com.naengjang_goat.inventory_system.alert.service;

import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.service.LowStockService;
import com.naengjang_goat.inventory_system.pricing.dto.PriceTrendResponse;
import com.naengjang_goat.inventory_system.pricing.service.PriceTrendService;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 재고 부족 + 가격 구매 적기 알림 트리거 서비스.
 *
 * 알림 타이밍:
 *   - 영업 시작(openTime) 3시간 전
 *   - 영업 종료(closeTime) 3시간 전
 *
 * 중복 방지:
 *   - 인메모리 Map (userId:slot → 마지막 발송 날짜)
 *   - 슬롯(open/close)별로 날짜가 바뀌기 전까지 1회만 발송
 *   - 서버 단일 인스턴스 운영 전제. 멀티 인스턴스로 확장 시 Redis SETNX로 교체.
 *
 * 알림 종류:
 *   1. 재고 부족 알림 — DepletionResult.orderAlert == true
 *   2. 가격 구매 적기 알림 — PriceTrendResponse.currentBuySignal == true
 *
 * 현재 알림 방식: 로그 출력 (확장 포인트 주석 표시)
 * TODO: 실제 푸시 알림 (FCM/APNS) 연동 시 sendPush() 메서드 추가
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertTriggerService {

    private static final int ALERT_BEFORE_HOURS  = 3;   // 영업 X시간 전 알림
    private static final int ALERT_WINDOW_MINUTES = 1;  // 체크 허용 오차 ±1분

    /** key = "userId:slot", value = 마지막 발송 날짜. 자정이 지나면 날짜가 달라져 자동 리셋. */
    private final Map<String, LocalDate> sentToday = new ConcurrentHashMap<>();

    private final StoreSettingsRepository settingsRepository;
    private final LowStockService lowStockService;
    private final PriceTrendService priceTrendService;

    /**
     * 전체 점주 설정을 조회해 현재 시각이 알림 구간인 점주를 찾고,
     * 해당 점주의 재고 부족 + 가격 알림을 발송한다.
     *
     * 스케줄러에서 1분마다 호출.
     */
    @Transactional(readOnly = true)
    public void triggerAlerts() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<StoreSettings> allSettings = settingsRepository.findAll();
        if (allSettings.isEmpty()) {
            return;
        }

        for (StoreSettings settings : allSettings) {
            Long userId = settings.getUser().getId();

            // 영업 시작 3시간 전 슬롯
            if (isAlertTime(settings.getOpenTime(), now) && markAlertSent(userId, "open")) {
                checkAndAlert(userId, "open");
            }
            // 영업 종료 3시간 전 슬롯
            if (isAlertTime(settings.getCloseTime(), now) && markAlertSent(userId, "close")) {
                checkAndAlert(userId, "close");
            }
        }
    }

    /**
     * 오늘 해당 슬롯 알림을 이미 보냈으면 false, 처음이면 기록 후 true 반환.
     * put()의 반환값이 오늘 날짜이면 이미 발송한 것.
     */
    private boolean markAlertSent(Long userId, String slot) {
        String key = userId + ":" + slot;
        LocalDate today = LocalDate.now();
        return !today.equals(sentToday.put(key, today));
    }

    /**
     * 점주의 재고 부족 알림 + 가격 구매 적기 알림을 처리한다.
     *
     * lowStockService 를 한 번만 호출해 두 알림에 공용으로 사용한다.
     *
     * @param userId 점주 ID
     * @param slot   로그 구분용 ("open" / "close")
     */
    private void checkAndAlert(Long userId, String slot) {
        // getTopLowStock 결과를 재고 알림 + 가격 알림 두 곳에 재사용
        List<LowStockItemDto> items = lowStockService.getTopLowStock(userId, Integer.MAX_VALUE);

        // ── 1. 재고 부족 알림 ─────────────────────────────────────────────
        List<String> stockAlertNames = items.stream()
                .filter(LowStockItemDto::isOrderAlert)
                .map(LowStockItemDto::getIngredientName)
                .collect(Collectors.toList());

        if (!stockAlertNames.isEmpty()) {
            log.warn("[AlertTrigger][{}] ⚠ userId={} 발주 필요 {}건: [{}]",
                    slot, userId, stockAlertNames.size(), String.join(", ", stockAlertNames));
            // ── 확장 포인트 ────────────────────────────────────────────────
            // TODO: FCM / APNS 푸시 알림 연동
            //   pushService.sendStockAlert(userId, stockAlertNames);
            // ──────────────────────────────────────────────────────────────
        } else {
            log.debug("[AlertTrigger][{}] userId={} 발주 필요 재료 없음", slot, userId);
        }

        // ── 2. 가격 구매 적기 알림 ────────────────────────────────────────
        List<String> buySignalNames = items.stream()
                .map(item -> {
                    try {
                        PriceTrendResponse trend = priceTrendService.getTrend(item.getIngredientId(), 30);
                        return trend.isCurrentBuySignal() ? item.getIngredientName() : null;
                    } catch (Exception e) {
                        log.debug("[AlertTrigger] buySignal 조회 실패 ingredientId={}: {}",
                                item.getIngredientId(), e.getMessage());
                        return null;
                    }
                })
                .filter(name -> name != null)
                .collect(Collectors.toList());

        if (!buySignalNames.isEmpty()) {
            log.info("[AlertTrigger][{}] 💰 userId={} 구매 적기 재료 {}건: [{}]",
                    slot, userId, buySignalNames.size(), String.join(", ", buySignalNames));
            // ── 확장 포인트 ────────────────────────────────────────────────
            // TODO: FCM / APNS 푸시 알림 연동
            //   pushService.sendPriceAlert(userId, buySignalNames);
            // ──────────────────────────────────────────────────────────────
        }
    }

    /**
     * 현재 시각(now)이 targetTime 기준 ALERT_BEFORE_HOURS 전 ±ALERT_WINDOW_MINUTES 구간인지 판단.
     *
     * 자정 경계(예: openTime=02:00 → alertTime=23:00)를 올바르게 처리하기 위해
     * 24시간 내 최단 거리(분) 기준으로 비교한다.
     *
     * @param targetTime openTime 또는 closeTime
     * @param now        현재 시각 (초 이하 절사)
     * @return 알림 구간 여부
     */
    private boolean isAlertTime(LocalTime targetTime, LocalTime now) {
        if (targetTime == null) return false;

        LocalTime alertTime = targetTime.minusHours(ALERT_BEFORE_HOURS);

        // ChronoUnit.MINUTES.between 은 자정 경계를 넘으면 큰 음수를 반환하므로 보정 필요
        long diff = ChronoUnit.MINUTES.between(alertTime, now);
        // 24시간 내 최단 거리로 정규화 [-720, 720]
        if (diff < -720) diff += 1440;
        if (diff >  720) diff -= 1440;

        return Math.abs(diff) <= ALERT_WINDOW_MINUTES;
    }
}
