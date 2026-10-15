package com.naengjang_goat.inventory_system.batch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.batch.config.BatchTimestampParams;
import com.naengjang_goat.inventory_system.batch.processor.KamisPriceProcessor;
import com.naengjang_goat.inventory_system.batch.processor.MarketPriceSaveService;
import com.naengjang_goat.inventory_system.batch.service.KamisApiClient;
import com.naengjang_goat.inventory_system.batch.writer.KamisPriceWriter;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.service.KamisPriceCalculator;
import com.naengjang_goat.inventory_system.pricing.service.PriceTrendService;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.root=WARN"})
@ActiveProfiles("test")
@Import(KamisIngestionIntegrationTest.TimeConfig.class)
class KamisIngestionIntegrationTest {
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired JobLauncher launcher;
    @Autowired @Qualifier("kamisPriceJob") Job job;
    @Autowired UserRepository users;
    @Autowired IngredientRepository ingredients;
    @Autowired MarketPriceRepository prices;
    @Autowired JdbcTemplate jdbc;
    @Autowired KamisPriceCalculator calculator;
    @Autowired PriceTrendService trends;
    @Autowired KamisApiClient client;
    @Autowired KamisPriceProcessor processor;
    @Autowired MarketPriceSaveService saver;
    @Autowired TransactionTemplate tx;
    @MockitoBean RestTemplate http;
    @MockitoSpyBean KamisPriceWriter writer;

    User ownerA, ownerB;
    Ingredient cabbageA, cabbageB;
    volatile String currentPrice;

    @BeforeEach void setup() {
        currentPrice = "2,000";
        ownerA = users.save(new User("kamis-a-" + UUID.randomUUID(), "fixture", "A", Role.OWNER));
        ownerB = users.save(new User("kamis-b-" + UUID.randomUUID(), "fixture", "B", Role.OWNER));
        cabbageA = ingredients.save(new Ingredient(ownerA, "배추", "g", BigDecimal.ONE));
        cabbageA.setKamisItemCode("211"); ingredients.save(cabbageA);
        cabbageB = ingredients.save(new Ingredient(ownerB, "배추", "g", BigDecimal.ONE));
        cabbageB.setKamisItemCode("211"); ingredients.save(cabbageB);
        var legacy = new MarketPrice();
        legacy.setIngredient(cabbageA); legacy.setRetailPrice("90000"); legacy.setWholesalePrice("80000");
        legacy.setUnit("kg"); legacy.setReportedDate(LocalDate.of(2026, 10, 12));
        prices.saveAndFlush(legacy);
        when(http.getForObject(anyString(), eq(String.class))).thenAnswer(call -> {
            String url = call.getArgument(0);
            if (!url.contains("p_item_category_code=200"))
                return "<document><data><error_code>200</error_code></data></document>";
            return "<document><data><error_code>000</error_code><item>"
                    + "<item_code>211</item_code><item_name>배추</item_name><kind_code>01</kind_code>"
                    + "<kind_name>가을</kind_name><rank_code>01</rank_code><rank>상품</rank><unit>10kg</unit>"
                    + "<dpr1>" + currentPrice + "</dpr1><day1>당일</day1><dpr4>1,000</dpr4>"
                    + "</item></data></document>";
        });
    }

    @AfterEach void cleanup() {
        if (cabbageA == null || cabbageB == null) return;
        jdbc.update("DELETE FROM market_price WHERE ingredient_id IN (?,?)", cabbageA.getId(), cabbageB.getId());
        ingredients.deleteAll(List.of(cabbageA, cabbageB));
        users.deleteAll(List.of(ownerA, ownerB));
    }

    JobParameters parameters() {
        return new JobParametersBuilder().addString("verification", UUID.randomUUID().toString()).toJobParameters();
    }

    long trustedCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_price WHERE ingredient_id IN (?,?) AND price_basis='WHOLESALE'",
                Long.class, cabbageA.getId(), cabbageB.getId());
    }

    @Test void realBatchStoresEveryOwnerWithCorrectDateBasisAndMatchingViews() throws Exception {
        var execution = launcher.run(job, parameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(1);
        assertThat(trustedCount()).isEqualTo(2);
        var row = jdbc.queryForMap("SELECT * FROM market_price WHERE ingredient_id=? AND price_basis='WHOLESALE'", cabbageA.getId());
        assertThat(row.get("wholesale_price")).isEqualTo("2000");
        assertThat(row.get("retail_price")).isNull();
        assertThat(row.get("reported_date").toString()).isEqualTo("2026-10-09");
        assertThat(row.get("unit")).isEqualTo("kg");
        var summary = calculator.buildKamis(cabbageA.getId());
        var trend = trends.getTrend(cabbageA.getId(), 30);
        assertThat(summary.getCurrentPricePerKg()).isEqualTo(2000);
        assertThat(summary.getMonthAvg()).isEqualTo(2000);
        assertThat(trend.getPoints().getLast().wholesalePrice()).isEqualTo(summary.getCurrentPricePerKg());
        assertThat(trend.getDataCoverage()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_price WHERE ingredient_id=? AND price_basis='LEGACY'", Long.class, cabbageA.getId())).isEqualTo(1);
    }

    @Test void repeatedCollectionUpdatesTheSameObservationWithoutAddingRows() throws Exception {
        assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        currentPrice = "2,500";
        assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(trustedCount()).isEqualTo(2);
        assertThat(calculator.buildKamis(cabbageA.getId()).getCurrentPricePerKg()).isEqualTo(2500);
    }

    @Test void mixedCodeAndNameMappingIncludesUnconfiguredOwnersButRespectsConflictingCodes() throws Exception {
        cabbageB.setKamisItemCode(null);
        ingredients.save(cabbageB);
        assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(trustedCount()).isEqualTo(2);
        jdbc.update("DELETE FROM market_price WHERE ingredient_id=?", cabbageB.getId());
        cabbageB.setKamisItemCode("212");
        ingredients.save(cabbageB);
        assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(trustedCount()).isEqualTo(1);
    }

    @Test void switchingRetailAndWholesaleDoesNotOverwriteOrAverageDifferentChannels() throws Exception {
        assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        try {
            ReflectionTestUtils.setField(client, "productClass", "01");
            currentPrice = "8,000";
            assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
            var retail = jdbc.queryForMap("SELECT retail_price,wholesale_price FROM market_price WHERE ingredient_id=? AND price_basis='RETAIL'", cabbageA.getId());
            assertThat(retail.get("retail_price")).isEqualTo("8000");
            assertThat(retail.get("wholesale_price")).isNull();
            assertThat(trustedCount()).isEqualTo(2);
            var summary = calculator.buildKamis(cabbageA.getId());
            assertThat(summary.getPriceBasis()).isEqualTo("WHOLESALE");
            assertThat(summary.getCurrentPricePerKg()).isEqualTo(2000);
            assertThat(summary.getMonthAvg()).isEqualTo(2000);
        } finally {
            ReflectionTestUtils.setField(client, "productClass", "02");
        }
    }

    @Test void failedChunkRollsBackStoredRowsAndTheSameJobCanRestartSafely() throws Exception {
        var failOnce = new AtomicBoolean(true);
        doAnswer(call -> {
            call.callRealMethod();
            if (failOnce.getAndSet(false)) throw new IllegalStateException("intentional failure after native upsert");
            return null;
        }).when(writer).write(any());
        var params = parameters();
        assertThat(launcher.run(job, params).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(trustedCount()).isZero();
        assertThat(launcher.run(job, params).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(trustedCount()).isEqualTo(2);
    }

    @Test void concurrentUpsertsCannotDuplicateTheSameObservation() throws Exception {
        var observations = processor.process(client.fetchAllCategories().getFirst());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = pool.invokeAll(List.of(
                    () -> { tx.executeWithoutResult(status -> saver.saveAll(observations)); return true; },
                    () -> { tx.executeWithoutResult(status -> saver.saveAll(observations)); return true; }));
            for (var future : futures) assertThat(future.get(20, TimeUnit.SECONDS)).isEqualTo(true);
        } finally { pool.shutdownNow(); }
        assertThat(trustedCount()).isEqualTo(2);
    }

    @Test void networkFailureMarksTheBatchFailedInsteadOfPretendingDataWasLoaded() throws Exception {
        doThrow(new ResourceAccessException("intentional network failure")).when(http).getForObject(anyString(), eq(String.class));
        assertThat(launcher.run(job, parameters()).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(trustedCount()).isZero();
    }
}
