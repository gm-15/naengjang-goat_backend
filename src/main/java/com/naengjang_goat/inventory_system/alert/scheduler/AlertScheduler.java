package com.naengjang_goat.inventory_system.alert.scheduler;

import com.naengjang_goat.inventory_system.alert.service.AlertTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재고 부족 알림 스케줄러.
 *
 * 실행 주기: 1분마다 (cron: 매 분 0초)
 *
 * 동작 흐름:
 *   1. AlertTriggerService.triggerAlerts() 호출
 *   2. 현재 시각 == (openTime - 3h) OR (closeTime - 3h) ±1분인 점주 탐색
 *   3. 해당 점주의 발주 알림 대상 재료 존재 시 알림 발송 (현재: 로그)
 *
 * @EnableScheduling 은 SchedulerConfig 에 선언되어 있음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final AlertTriggerService alertTriggerService;

    /**
     * 매 분 0초에 실행.
     * 시간 부하가 낮아 cron으로 충분 (배치 chunk 처리 불필요).
     */
    @Scheduled(cron = "0 * * * * *")
    public void runAlertCheck() {
        log.debug("[AlertScheduler] 재고 알림 체크 실행");
        try {
            alertTriggerService.triggerAlerts();
        } catch (Exception e) {
            // 스케줄러 예외가 다음 실행을 막지 않도록 catch
            log.error("[AlertScheduler] 알림 체크 중 오류: {}", e.getMessage(), e);
        }
    }
}
