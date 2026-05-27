package com.naengjang_goat.inventory_system.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KAMIS 일일 배치 스케줄러.
 * - 매일 03:00 KST 에 KAMIS API 호출 → MarketPrice 적재.
 * - cron 표현식 (sec min hour day month dow): "0 0 3 * * *" = 03:00:00.
 */
@Slf4j
@Component
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
