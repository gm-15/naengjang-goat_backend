package com.naengjang_goat.inventory_system.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
