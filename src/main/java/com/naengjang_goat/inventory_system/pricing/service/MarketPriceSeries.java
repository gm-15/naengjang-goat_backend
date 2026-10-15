package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 같은 출처·유통 구분·품종·등급의 관측만 비교하는 공통 원/kg 시계열. */
@Service
@RequiredArgsConstructor
public class MarketPriceSeries {
    private final MarketPriceRepository prices;
    private final Clock clock;
    private static final Pattern WEIGHT = Pattern.compile("(?i)^(\\d+(?:\\.\\d+)?)?(kg|g)$");

    public record Point(LocalDate date, Long wholesale, Long retail, MarketPrice observation) {
        public long price() { return wholesale != null ? wholesale : retail; }
    }

    public List<Point> load(Long ingredientId, int days) {
        LocalDate today = LocalDate.now(clock);
        var candidates = prices.findAllByIngredientIdAndReportedDateBetween(
                ingredientId, today.minusDays((long) days + 29), today).stream()
                .filter(row -> row.getReportedDate() != null && !row.getReportedDate().isAfter(today))
                .map(this::point).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Point::date).reversed()
                        .thenComparingInt(p -> p.wholesale() != null ? 0 : 1)
                        .thenComparing(p -> value(p.observation().getSource()))
                        .thenComparing(p -> value(p.observation().getGrade()))
                        .thenComparing(p -> value(p.observation().getKindName()))
                        .thenComparing(p -> value(p.observation().getObservationKey()))
                        .thenComparing(p -> p.observation().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (candidates.isEmpty()) return List.of();
        String selected = identity(candidates.getFirst().observation());
        var byDay = new TreeMap<LocalDate, Point>();
        for (Point candidate : candidates) {
            if (!selected.equals(identity(candidate.observation()))) continue;
            byDay.merge(candidate.date(), candidate, (a, b) -> id(a) >= id(b) ? a : b);
        }
        return List.copyOf(byDay.values());
    }

    private Point point(MarketPrice row) {
        // 기준을 확정할 수 없는 과거 KAMIS 자료는 보존하되 평균/발주 판단에서 제외한다.
        String basis = row.getPriceBasis();
        if (!"WHOLESALE".equals(basis) && !"RETAIL".equals(basis)) return null;
        Long amount = perKg("WHOLESALE".equals(basis) ? row.getWholesalePrice() : row.getRetailPrice(), row.getUnit());
        if (amount == null || amount <= 0) return null;
        return new Point(row.getReportedDate(), "WHOLESALE".equals(basis) ? amount : null,
                "RETAIL".equals(basis) ? amount : null, row);
    }

    private String identity(MarketPrice row) {
        return String.join("\u001f", value(row.getSource()), value(row.getPriceBasis()),
                value(row.getObservationKey()));
    }
    private long id(Point p) { return p.observation().getId() == null ? 0 : p.observation().getId(); }
    private String value(String value) { return value == null ? "" : value; }

    public static Long average(List<Point> points, LocalDate end, int days) {
        var average = points.stream().filter(p -> !p.date().isAfter(end)
                        && !p.date().isBefore(end.minusDays(days - 1L)))
                .mapToLong(Point::price).average();
        return average.isPresent() ? Math.round(average.getAsDouble()) : null;
    }

    static Long perKg(String raw, String unit) {
        if (raw == null || unit == null) return null;
        try {
            var match = WEIGHT.matcher(unit.replaceAll("\\s", ""));
            if (!match.matches()) return null;
            var weight = new BigDecimal(match.group(1) == null ? "1" : match.group(1));
            if (weight.signum() <= 0) return null;
            var kilograms = "g".equalsIgnoreCase(match.group(2)) ? weight.movePointLeft(3) : weight;
            return new BigDecimal(raw.replace(",", "").trim()).divide(kilograms, 0, RoundingMode.HALF_UP).longValueExact();
        } catch (RuntimeException invalid) { return null; }
    }
}
