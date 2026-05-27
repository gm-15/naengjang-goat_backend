package com.naengjang_goat.inventory_system.batch.job;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.processor.KamisPriceProcessor;
import com.naengjang_goat.inventory_system.batch.reader.KamisApiReader;
import com.naengjang_goat.inventory_system.batch.tasklet.BuySignalNotifyTasklet;
import com.naengjang_goat.inventory_system.batch.writer.KamisPriceWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class KamisPriceBatchJobConfig {

    private final KamisApiReader reader;
    private final KamisPriceProcessor processor;
    private final KamisPriceWriter writer;
    private final BuySignalNotifyTasklet buySignalNotifyTasklet;

    @Bean
    public Job kamisPriceJob(JobRepository jobRepository,
                             Step kamisPriceStep,
                             Step buySignalNotifyStep) {
        return new JobBuilder("kamisPriceJob", jobRepository)
                .start(kamisPriceStep)
                .next(buySignalNotifyStep)   // 수집 완료 후 buySignal 알림
                .build();
    }

    @Bean
    public Step kamisPriceStep(JobRepository jobRepository,
                               PlatformTransactionManager tx) {
        return new StepBuilder("kamisPriceStep", jobRepository)
                .<KamisPriceDto, MarketPrice>chunk(50, tx)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Step buySignalNotifyStep(JobRepository jobRepository,
                                   PlatformTransactionManager tx) {
        return new StepBuilder("buySignalNotifyStep", jobRepository)
                .tasklet(buySignalNotifyTasklet, tx)
                .build();
    }
}
