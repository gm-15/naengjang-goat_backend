package com.naengjang_goat.inventory_system.analysis.batch.job;

import com.naengjang_goat.inventory_system.analysis.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.analysis.batch.processor.KamisPriceProcessor;
import com.naengjang_goat.inventory_system.analysis.batch.reader.KamisApiReader;
import com.naengjang_goat.inventory_system.analysis.batch.writer.KamisPriceWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class KamisPriceBatchJobConfig {

    private final KamisApiReader reader;
    private final KamisPriceProcessor processor;
    private final KamisPriceWriter writer;

    @Bean
    public Job kamisDailyPriceJob(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new JobBuilder("kamisDailyPriceJob", jobRepository)
                .start(kamisPriceStep(jobRepository, txManager))
                .build();
    }

    @Bean
    public Step kamisPriceStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("kamisPriceStep", jobRepository)
                .<KamisPriceDto, KamisPriceDto>chunk(50, txManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
