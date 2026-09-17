package com.naengjang_goat.inventory_system.global.config;

import com.naengjang_goat.inventory_system.batch.config.BatchTimestampParams;
import com.naengjang_goat.inventory_system.batch.ekape.EkapeScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 KAMIS · EKAPE 가격 수집을 자동으로 1회 트리거.
 *
 * 매일 03시·03:30 cron 이 동작하지만, 시연 환경에서는 부팅 직후 데이터가 없어
 * 시안 가격 카드·30일 추이 차트가 비어 보이는 문제를 해결.
 *
 * 흐름:
 *  1. DataInitializer 시드 적용 대기 (5초 sleep)
 *  2. Virtual Thread (Java 21) 로 백그라운드 실행 — 부팅 시간 영향 없음
 *  3. KAMIS Job → EKAPE Scheduler 순차 호출
 *  4. 한쪽 실패해도 다른 쪽은 계속 진행 (try/catch 분리)
 *  5. 중복 적재는 Writer 의 source 기준 skip 으로 자동 처리
 *
 * @author sim
 * @since 2026-06-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
@Order(Ordered.LOWEST_PRECEDENCE)
public class BatchAutoTrigger implements ApplicationRunner {

    private static final long STARTUP_DELAY_MS = 5_000L;

    private final JobLauncher    jobLauncher;
    private final Job            kamisPriceJob;
    private final EkapeScheduler ekapeScheduler;

    @Override
    public void run(ApplicationArguments args) {
        Thread.startVirtualThread(this::triggerBoth);
    }

    private void triggerBoth() {
        try {
            Thread.sleep(STARTUP_DELAY_MS);  // DataInitializer 시드 완료 대기
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return;
        }

        log.info("[BatchAutoTrigger] 부팅 시 KAMIS · EKAPE 자동 트리거 시작");

        // KAMIS (30일 backfill — KamisApiClient.fetchAllCategories)
        try {
            JobExecution execution = jobLauncher.run(kamisPriceJob, BatchTimestampParams.now());
            log.info("[BatchAutoTrigger] KAMIS Job 트리거 완료 — executionId={} status={}",
                    execution.getId(), execution.getStatus());
        } catch (Exception e) {
            log.error("[BatchAutoTrigger] KAMIS Job 실패 (부팅에는 영향 없음)", e);
        }

        // EKAPE (LIVESTOCK 31일 backfill — EkapeScheduler 내부)
        try {
            ekapeScheduler.collect();
            log.info("[BatchAutoTrigger] EKAPE 트리거 완료");
        } catch (Exception e) {
            log.error("[BatchAutoTrigger] EKAPE 실패 (부팅에는 영향 없음)", e);
        }

        log.info("[BatchAutoTrigger] 부팅 트리거 종료");
    }
}
