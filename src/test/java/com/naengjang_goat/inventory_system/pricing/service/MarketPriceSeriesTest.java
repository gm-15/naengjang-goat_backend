package com.naengjang_goat.inventory_system.pricing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketPriceSeriesTest {
    MarketPriceRepository repository;
    MarketPriceSeries series;
    Clock clock;
    List<MarketPrice> data;

    @BeforeEach void setup() {
        repository = mock(MarketPriceRepository.class);
        clock = Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        series = new MarketPriceSeries(repository, clock);
        data = new ArrayList<>();
        when(repository.findAllByIngredientIdAndReportedDateBetween(eq(1L), any(), any())).thenAnswer(call -> data);
    }

    MarketPrice row(long id, String day, String basis, String price, String key) {
        var row = new MarketPrice();
        row.setId(id); row.setReportedDate(LocalDate.parse(day)); row.setUnit("kg");
        row.setSource("KAMIS"); row.setPriceBasis(basis); row.setObservationKey(key);
        if ("WHOLESALE".equals(basis)) row.setWholesalePrice(price); else row.setRetailPrice(price);
        data.add(row); return row;
    }

    @Test void legacyAndDifferentChannelsAreNotMixedIntoTheAverage() {
        row(1, "2026-10-08", "RETAIL", "8000", "retail");
        row(2, "2026-10-09", "WHOLESALE", "2000", "wholesale");
        row(3, "2026-10-12", "LEGACY", "90000", null);
        var response = new KamisPriceCalculator(series).buildKamis(1L);
        assertThat(response.getCurrentPricePerKg()).isEqualTo(2000);
        assertThat(response.getMonthAvg()).isEqualTo(2000);
        assertThat(response.getPriceDate()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test void sameDayDuplicatesUseLatestRowAndCountOneActualObservationDay() {
        row(1, "2026-10-09", "WHOLESALE", "1000", "same");
        row(2, "2026-10-09", "WHOLESALE", "2000", "same");
        var points = series.load(1L, 30);
        assertThat(points).hasSize(1);
        assertThat(points.getFirst().price()).isEqualTo(2000);
    }

    @Test void gradesAndKindsRemainSeparateSeries() {
        row(1, "2026-10-08", "WHOLESALE", "8000", "kind-a-grade-a");
        row(2, "2026-10-09", "WHOLESALE", "2000", "kind-b-grade-b");
        assertThat(new KamisPriceCalculator(series).buildKamis(1L).getMonthAvg()).isEqualTo(2000);
    }

    @Test void detailAndTrendUseTheSameCurrentPriceAndUnit() {
        row(1, "2026-10-09", "WHOLESALE", "2000", "same");
        var ingredient = mock(Ingredient.class);
        var ingredients = mock(IngredientRepository.class);
        when(ingredients.findById(1L)).thenReturn(Optional.of(ingredient));
        var detail = new KamisPriceCalculator(series).buildKamis(1L);
        var trend = new PriceTrendService(series, ingredients, clock).getTrend(1L, 30);
        assertThat(trend.getPoints().getLast().wholesalePrice()).isEqualTo(detail.getCurrentPricePerKg());
        assertThat(trend.getPoints().getLast().retailPrice()).isNull();
        assertThat(trend.getPriceBasis()).isEqualTo("WHOLESALE");
        assertThat(trend.getUnit()).isEqualTo("kg");
    }

    @Test void ekape100gPricesAreNormalizedToKgInBothViews() {
        var row = row(1, "2026-10-09", "RETAIL", "1234.5", null);
        row.setSource("EKAPE"); row.setUnit("100g");
        assertThat(new KamisPriceCalculator(series).buildKamis(1L).getCurrentPricePerKg()).isEqualTo(12345);
    }

    @Test void calendarWindowExcludesOldRowsRatherThanTakingTheLastSevenRows() {
        row(1, "2026-09-01", "WHOLESALE", "9000", "same");
        row(2, "2026-10-09", "WHOLESALE", "2000", "same");
        var summary = new KamisPriceCalculator(series).buildKamis(1L);
        assertThat(summary.getWeekAvg()).isEqualTo(2000);
        assertThat(summary.getMonthAvg()).isEqualTo(2000);
    }

    @Test void unknownWeightAndFutureObservationsDoNotBecomeKgPrices() {
        row(1, "2026-10-09", "WHOLESALE", "2000", "same").setUnit("1포기");
        row(2, "2026-10-13", "WHOLESALE", "3000", "same");
        assertThat(new KamisPriceCalculator(series).buildKamis(1L)).isNull();
    }
}
