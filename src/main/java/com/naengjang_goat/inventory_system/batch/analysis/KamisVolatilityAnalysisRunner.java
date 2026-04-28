package com.naengjang_goat.inventory_system.batch.analysis;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KAMIS 5년치 변동성 분석 — buySignal 임계값 결정용 일회성 분석 도구.
 *
 * 실행 방법:
 *   IntelliJ: 이 파일 열고 main() 옆 ▶ 클릭
 *   또는: ./gradlew run --main=...batch.analysis.KamisVolatilityAnalysisRunner
 *
 * 분석 로직:
 *   1. 카테고리별 대표 품목 5년치(2020~2024) 월별 도매가 수집
 *   2. 연도별 CV = std_dev(월별가격 12개) / mean(월별가격 12개)
 *   3. 5개 연도 CV 중 Z-score |z| > 1.3 이상값 제거
 *   4. 남은 CV 평균 = buySignal threshold
 *
 * 결과를 KamisCategory enum 에 반영할 것.
 *
 * KAMIS API: periodPriceList 액션 (월별 시세 반환)
 * 문서: https://www.kamis.or.kr/customer/reference/openapi_list.do
 */
public class KamisVolatilityAnalysisRunner {

    // ── API 설정 (application.properties 와 동기화) ──────────────────────────
    private static final String API_KEY  = "c7db4fde-1344-4f12-b708-e5f54a4c25f5";
    private static final String API_ID   = "6879";
    private static final String BASE_URL = "https://www.kamis.or.kr/service/price/xml.do";

    // 분석 연도 범위
    private static final int YEAR_FROM = 2020;
    private static final int YEAR_TO   = 2024;

    // Z-score 이상값 제거 기준 (5개 중 ±1.3σ 밖 제거)
    private static final double ZSCORE_THRESHOLD = 1.3;

    // ── 분석 대상 품목 ─────────────────────────────────────────────────────────
    enum TargetItem {
        // 채소류
        CABBAGE   ("배추",        "211", "03", "01", Category.VEGETABLES),
        GREEN_ONION("대파",       "252", "03", "01", Category.VEGETABLES),
        GARLIC    ("마늘(깐마늘)", "231", "03", "01", Category.VEGETABLES),
        ONION     ("양파",        "227", "03", "01", Category.VEGETABLES),

        // 축산물
        PORK      ("돼지고기(삼겹살)", "413", "04", "01", Category.LIVESTOCK),
        BEEF      ("소고기(한우등심)", "411", "04", "01", Category.LIVESTOCK),
        CHICKEN   ("닭고기",          "421", "03", "01", Category.LIVESTOCK),
        EGGS      ("달걀",            "511", "04", "01", Category.LIVESTOCK),

        // 수산물
        MACKEREL  ("고등어", "521", "04", "01", Category.SEAFOOD),
        POLLACK   ("명태",   "522", "04", "01", Category.SEAFOOD),

        // 과일류
        APPLE     ("사과", "321", "03", "01", Category.FRUITS),
        PEAR      ("배",   "322", "03", "01", Category.FRUITS),

        // 곡물류
        RICE      ("쌀", "111", "01", "01", Category.GRAINS);

        // 가공식품(간장, 식용유 등)은 KAMIS 미포함 → Category.PROCESSED 고정 3%

        final String name;
        final String productNo;
        final String kindCode;
        final String gradeRank;
        final Category category;

        TargetItem(String name, String productNo, String kindCode,
                   String gradeRank, Category category) {
            this.name      = name;
            this.productNo = productNo;
            this.kindCode  = kindCode;
            this.gradeRank = gradeRank;
            this.category  = category;
        }
    }

    enum Category {
        VEGETABLES("채소류"),
        LIVESTOCK ("축산물"),
        SEAFOOD   ("수산물"),
        FRUITS    ("과일류"),
        GRAINS    ("곡물류"),
        PROCESSED ("가공식품·조미료 (KAMIS 미포함 → 고정 3%)");

        final String label;
        Category(String label) { this.label = label; }
    }

    // ── main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.out.println("=== KAMIS 변동성 분석 시작 (" + YEAR_FROM + "~" + YEAR_TO + ") ===\n");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 품목별 분석 결과: item → { year → CV }
        Map<TargetItem, Map<Integer, Double>> itemYearCv = new LinkedHashMap<>();

        for (TargetItem item : TargetItem.values()) {
            Map<Integer, Double> yearCv = new LinkedHashMap<>();

            for (int year = YEAR_FROM; year <= YEAR_TO; year++) {
                List<Long> monthlyPrices = fetchMonthlyWholesale(http, item, year);

                if (monthlyPrices.size() < 3) {
                    System.out.printf("  [SKIP] %s %d년 데이터 부족 (%d개)%n",
                            item.name, year, monthlyPrices.size());
                    continue;
                }

                double cv = computeCv(monthlyPrices);
                yearCv.put(year, cv);
                System.out.printf("  %s %d년: 월별가격 %d개, CV=%.3f%n",
                        item.name, year, monthlyPrices.size(), cv);
            }

            itemYearCv.put(item, yearCv);
        }

        // 카테고리별 집계
        System.out.println("\n=== 카테고리별 임계값 계산 ===\n");

        Map<Category, Double> categoryThreshold = new LinkedHashMap<>();

        for (Category cat : Category.values()) {
            if (cat == Category.PROCESSED) {
                categoryThreshold.put(cat, 0.03);
                continue;
            }

            // 해당 카테고리 품목들의 모든 연도 CV 수집
            List<Double> allCvs = itemYearCv.entrySet().stream()
                    .filter(e -> e.getKey().category == cat)
                    .flatMap(e -> e.getValue().values().stream())
                    .collect(Collectors.toList());

            if (allCvs.isEmpty()) {
                System.out.printf("[%s] 데이터 없음 → 기본값 0.10 적용%n", cat.label);
                categoryThreshold.put(cat, 0.10);
                continue;
            }

            List<Double> cleaned = removeOutliers(allCvs);
            double threshold = cleaned.stream().mapToDouble(d -> d).average().orElse(0.10);
            categoryThreshold.put(cat, threshold);

            System.out.printf("[%s]%n", cat.label);
            System.out.printf("  원본 CV: %s%n", formatList(allCvs));
            System.out.printf("  제거후 CV: %s%n", formatList(cleaned));
            System.out.printf("  → threshold = %.4f (%.1f%%)%n%n", threshold, threshold * 100);
        }

        // 최종 결과 출력
        printResult(categoryThreshold);
    }

    // ── KAMIS periodPriceList 호출 → 월별 도매가 반환 ──────────────────────────
    private static List<Long> fetchMonthlyWholesale(HttpClient http,
                                                     TargetItem item,
                                                     int year) throws Exception {
        String url = BASE_URL
                + "?action=periodPriceList"
                + "&p_startday=" + year + "-01-01"
                + "&p_endday="   + year + "-12-31"
                + "&p_productno=" + item.productNo
                + "&p_kindcode="  + item.kindCode
                + "&p_graderank=" + item.gradeRank
                + "&p_countycode=1101"   // 서울
                + "&p_cert_key=" + API_KEY
                + "&p_cert_id="  + API_ID
                + "&p_returntype=xml";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            System.out.printf("  [ERROR] %s %d년 HTTP %d%n", item.name, year, res.statusCode());
            return List.of();
        }

        return parseWholesalePrices(res.body());
    }

    // ── XML 파싱 — dpr4(도매가) 추출 ─────────────────────────────────────────
    private static List<Long> parseWholesalePrices(String xml) {
        List<Long> prices = new ArrayList<>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // error_code 확인
            NodeList errNodes = doc.getElementsByTagName("error_code");
            if (errNodes.getLength() > 0) {
                String err = errNodes.item(0).getTextContent().trim();
                if (!"000".equals(err)) return prices;
            }

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element el = (Element) items.item(i);
                String dpr4 = getTagText(el, "dpr4"); // 도매가
                if (dpr4 == null || dpr4.isBlank() || "-".equals(dpr4)) continue;

                try {
                    long price = Long.parseLong(dpr4.replace(",", "").trim());
                    if (price > 0) prices.add(price);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            System.out.println("  [PARSE ERROR] " + e.getMessage());
        }
        return prices;
    }

    // ── 통계 유틸 ──────────────────────────────────────────────────────────────

    /** CV = 표준편차 / 평균 */
    private static double computeCv(List<Long> prices) {
        double mean = prices.stream().mapToLong(v -> v).average().orElse(0);
        if (mean == 0) return 0;
        double variance = prices.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance) / mean;
    }

    /** Z-score |z| > threshold 인 값 제거 */
    private static List<Double> removeOutliers(List<Double> cvs) {
        if (cvs.size() <= 2) return new ArrayList<>(cvs);

        double mean = cvs.stream().mapToDouble(d -> d).average().orElse(0);
        double std  = Math.sqrt(cvs.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0));

        if (std == 0) return new ArrayList<>(cvs);

        List<Double> cleaned = cvs.stream()
                .filter(v -> Math.abs((v - mean) / std) <= ZSCORE_THRESHOLD)
                .collect(Collectors.toList());

        // 최소 2개는 유지
        return cleaned.size() >= 2 ? cleaned : new ArrayList<>(cvs);
    }

    // ── 출력 ──────────────────────────────────────────────────────────────────

    private static void printResult(Map<Category, Double> thresholds) {
        System.out.println("=".repeat(60));
        System.out.println("=== KamisCategory enum 추천값 ===");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("  아래 값을 KamisCategory enum buySignalThreshold에 반영하세요:\n");

        thresholds.forEach((cat, t) ->
                System.out.printf("  %-12s  threshold = %.4f  (%.1f%%)  // %s%n",
                        cat.name(), t, t * 100, cat.label));

        System.out.println();
        System.out.println("  예시 코드:");
        System.out.println("  enum KamisCategory {");
        thresholds.forEach((cat, t) ->
                System.out.printf("    %-12s(%.2f),  // %s%n", cat.name(), t, cat.label));
        System.out.println("    ;");
        System.out.println("    final double buySignalThreshold;");
        System.out.println("  }");
        System.out.println();
        System.out.println("  buySignal 조건: currentWholesalePrice < monthAvg × (1 - threshold)");
    }

    private static String getTagText(Element el, String tag) {
        NodeList list = el.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
    }

    private static String formatList(List<Double> list) {
        return list.stream()
                .map(v -> String.format("%.3f", v))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
