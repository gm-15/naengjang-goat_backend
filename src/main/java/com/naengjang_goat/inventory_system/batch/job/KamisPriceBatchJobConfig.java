package com.naengjang_goat.inventory_system.batch.job;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.processor.KamisPriceProcessor;
import com.naengjang_goat.inventory_system.batch.reader.KamisApiReader;
import com.naengjang_goat.inventory_system.batch.writer.KamisPriceWriter;
import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
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

    @Bean
    public Job kamisPriceJob(JobRepository jobRepository, Step kamisPriceStep) {
        return new JobBuilder("kamisPriceJob", jobRepository)
                .start(kamisPriceStep)
                .build();
    }

    @Bean
    public Step kamisPriceStep(JobRepository jobRepository,
                               PlatformTransactionManager tx) {

        return new StepBuilder("kamisPriceStep", jobRepository)
                .<KamisPriceDto, PriceHistory>chunk(50, tx)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
