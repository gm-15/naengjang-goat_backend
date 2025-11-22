package com.naengjang_goat.inventory_system.analysis.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job kamisDailyPriceJob;

    @Scheduled(cron = "0 0 3 * * *")
    public void runKamisJob() {
        try {
            jobLauncher.run(kamisDailyPriceJob, new org.springframework.batch.core.JobParameters());
            log.info("✅ KAMIS Batch 실행 완료");
        } catch (Exception e) {
            log.error("❌ KAMIS Batch 실행 실패", e);
        }
    }
}
