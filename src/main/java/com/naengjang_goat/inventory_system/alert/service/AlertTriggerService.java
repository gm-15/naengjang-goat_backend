package com.naengjang_goat.inventory_system.alert.service;

import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.service.LowStockService;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 재고 부족 알림 트리거 서비스.
 *
 * 알림 타이밍:
 *   - 영업 시작(openTime) 3시간 전
 *   - 영업 종료(closeTime) 3시간 전
 *
 * 알림 조건:
 *   - DepletionResult.orderAlert == true (grade==DANGER AND 소진예정일 < 다음발주일)
 *
 * 시간 허용 오차:
 *   - ±1분 윈도우 (스케줄러가 1분마다 실행되므로)
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

    private final StoreSettingsRepository settingsRepository;
    private final LowStockService lowStockService;

    /**
     * 전체 점주 설정을 조회해 현재 시각이 알림 구간인 점주를 찾고,
     * 해당 점주의 재고 부족 알림을 발송한다.
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
            boolean shouldAlert =
                    isAlertTime(settings.getOpenTime(), now)
                 || isAlertTime(settings.getCloseTime(), now);

            if (shouldAlert) {
                Long userId = settings.getUser().getId();
                checkAndAlert(userId);
            }
        }
    }

    /**
     * 점주의 저재고 목록을 계산해 발주 알림 대상 재료가 있으면 알림 발송.
     *
     * @param userId 점주 ID
     */
    private void checkAndAlert(Long userId) {
        List<LowStockItemDto> lowStockItems = lowStockService.getTopLowStock(userId, Integer.MAX_VALUE);

        List<LowStockItemDto> alertTargets = lowStockItems.stream()
                .filter(LowStockItemDto::isOrderAlert)
                .collect(Collectors.toList());

        if (alertTargets.isEmpty()) {
            log.debug("[AlertTrigger] userId={} 발주 필요 재료 없음", userId);
            return;
        }

        String ingredientNames = alertTargets.stream()
                .map(LowStockItemDto::getIngredientName)
                .collect(Collectors.joining(", "));

        log.warn("[AlertTrigger] ⚠ userId={} 발주 필요 재료 {}건: [{}]",
                userId, alertTargets.size(), ingredientNames);

        // ── 확장 포인트 ────────────────────────────────────────────
        // TODO: FCM / APNS 푸시 알림 연동
        //   pushService.sendStockAlert(userId, alertTargets);
        // ──────────────────────────────────────────────────────────
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
