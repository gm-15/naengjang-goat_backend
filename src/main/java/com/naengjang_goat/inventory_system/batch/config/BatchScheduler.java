package com.naengjang_goat.inventory_system.batch.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job kamisDailyPriceJob;

    //@Scheduled(cron = "0 0 6 * * *")
    @Scheduled(fixedDelay = 5000)
    public void runKamisDailyJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(kamisDailyPriceJob, params);
    }
}
