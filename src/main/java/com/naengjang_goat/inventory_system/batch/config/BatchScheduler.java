package com.naengjang_goat.inventory_system.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: KamisPriceBatchJobConfig 비활성화로 Job 빈 없음
 * 재활성화 조건: KAMIS 배치 복구 시
 * 비활성화 일자: 2026-03-15
 */
@Slf4j
// @Component  // [v2.1 비활성화]
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job kamisPriceJob;

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        try {
            jobLauncher.run(kamisPriceJob, BatchTimestampParams.now());
        } catch (Exception e) {
            log.error("[KAMIS-BATCH] Scheduler Error", e);
        }
    }
}
