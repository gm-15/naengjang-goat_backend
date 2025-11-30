package com.naengjang_goat.inventory_system.batch.job;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.processor.KamisPriceProcessor;
import com.naengjang_goat.inventory_system.batch.reader.KamisApiReader;
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

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final KamisApiReader reader;
    private final KamisPriceProcessor processor;
    private final KamisPriceWriter writer;

    @Bean
    public Step kamisDailyStep() {
        return new StepBuilder("kamisDailyStep", jobRepository)
                .<KamisPriceDto, com.naengjang_goat.inventory_system.analysis.domain.PriceHistory>chunk(30, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job kamisDailyPriceJob() {
        return new JobBuilder("kamisDailyPriceJob", jobRepository)
                .start(kamisDailyStep())
                .build();
    }
}
