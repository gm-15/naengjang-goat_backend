package com.naengjang_goat.inventory_system.batch.analysis;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KAMIS 변동성 분석 — buySignal 임계값 결정용 일회성 분석 도구.
 *
 * ▶ 실행: IntelliJ에서 main() 옆 ▶ 클릭
 *
 * ▶ API: periodProductList (기간별 품목별 도·소매가격정보)
 *   - p_startday / p_endday 무시됨 → API가 최근 1년 데이터를 자동 반환
 *   - 가격 필드: <price> (dpr4/dpr1 아님)
 *   - p_productclscode=02 (도매) 만 데이터 있음 (01=소매는 error_code=001)
 *   - countyname=평년 항목은 평년 기준선 → 제외, countyname=평균 항목만 사용
 *
 * ▶ 분석 방법:
 *   1. 품목별 API 1회 호출 → 최근 12개월 일별 가격 수집
 *   2. 월별 평균가 계산 (yyyy + regday의 월 기준 그루핑)
 *   3. CV = std_dev(월별평균) / mean(월별평균)
 *   4. 카테고리 내 품목 CV 평균 = threshold
 */
public class KamisVolatilityAnalysisRunner {

    private static final String API_KEY  = "c7db4fde-1344-4f12-b708-e5f54a4c25f5";
    private static final String API_ID   = "6879";
    private static final String BASE_URL = "https://www.kamis.or.kr/service/price/xml.do";

    // 5년 기간 (2021-01-01 ~ 2026-04-28)
    private static final String START_DAY = "20210101";
    private static final String END_DAY   = "20260428";

    private static final double ZSCORE_THRESHOLD = 1.3;

    // ── 분석 대상 품목 ────────────────────────────────────────────────────────
    // 코드 출처: KAMIS OpenAPI 농축수산물 품목 및 등급 코드표 (공식 첨부파일)
    // cat=100 식량작물 / cat=200 채소류 / cat=300 특용·버섯
    // cat=400 과일류   / cat=500 계란·우유 / cat=600 수산물
    //
    // ※ 축산물(한우·돼지·닭)은 periodProductList 대상 아님.
    //   cat=400은 과일류 코드 (411=사과, 412=배, 413=복숭아, 421=감귤).
    //   축산물 가격은 축산물품질평가원(EKAPE) 별도 API — 본 분석 범위 외.
    //   → LIVESTOCK threshold는 별도 고정값으로 관리 (Category.LIVESTOCK 참고).
    //
    // preferRetail=true : cls=01(소매) 먼저 시도 — 과일류는 소매가격 기반
    enum TargetItem {
        // 채소류 (cat=200) — yearlySalesList 검증 기준 코드
        // ※ 무 코드 212→231 수정 (212=양배추, 231=봄무 확인)
        // ※ 대파 kind=01 → kind 생략 (246+no kind = 파 전체 데이터)
        // ※ 건고추 kind=01 → kind 생략 (243+no kind = 붉은고추 전체 데이터, 건고추 아님 주의)
        CABBAGE    ("배추",          "200", "211", "01", "04", false, Category.VEGETABLES),
        ONION      ("양파",          "200", "245", "02", "04", false, Category.VEGETABLES),  // kind=02: 햇양파
        GARLIC     ("마늘(깐마늘)", "200", "258", "01", "04", false, Category.VEGETABLES),
        GREEN_ONION("파(대파)",      "200", "246", "",   "04", false, Category.VEGETABLES),  // kind 생략: 파 전체
        RADISH     ("무",            "200", "231", "01", "04", false, Category.VEGETABLES),  // 봄무 (212≠무, 212=양배추)
        HOT_PEPPER ("붉은고추",      "200", "243", "",   "04", false, Category.VEGETABLES),  // kind 생략: 붉은고추 전체

        // 수산물 (cat=600) — 3품목 샘플
        MACKEREL   ("고등어", "600", "611", "",   "04", false, Category.SEAFOOD),  // kind 생략: 고등어 전체
        POLLACK    ("명태",   "600", "615", "02", "04", false, Category.SEAFOOD),  // kind=02: 냉동
        SQUID      ("오징어", "600", "612", "01", "04", false, Category.SEAFOOD),

        // 과일류 (cat=400) — yearlySalesList 검증 기준 코드
        // ※ 사과 kind=05: 후지(부사) — kind=01=홍옥(데이터 없음)
        APPLE      ("사과(후지)", "400", "411", "05", "",   true,  Category.FRUITS),  // kind=05: 후지
        PEAR       ("배(신고)",   "400", "412", "01", "04", true,  Category.FRUITS),

        // 곡물류 (cat=100) — 1품목
        RICE       ("쌀",    "100", "111", "01", "01", false, Category.GRAINS);

        final String name;
        final String categoryCode;
        final String itemCode;
        final String kindCode;
        final String gradeCode;
        final boolean preferRetail; // true = cls=01 먼저 (과일류)
        final Category category;

        TargetItem(String name, String categoryCode, String itemCode,
                   String kindCode, String gradeCode, boolean preferRetail, Category category) {
            this.name         = name;
            this.categoryCode = categoryCode;
            this.itemCode     = itemCode;
            this.kindCode     = kindCode;
            this.gradeCode    = gradeCode;
            this.preferRetail = preferRetail;
            this.category     = category;
        }
    }

    enum Category {
        VEGETABLES("채소류"),
        LIVESTOCK ("축산물 ※ periodProductList 미지원 → EKAPE API 별도, 고정값 0.08 사용"),
        SEAFOOD   ("수산물"),
        FRUITS    ("과일류"),
        GRAINS    ("곡물류"),
        PROCESSED ("가공식품·조미료 (고정 3%)");

        final String label;
        Category(String label) { this.label = label; }
    }

    // ── main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        System.setOut(out);

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        out.println("=== KAMIS 변동성 분석 ===");
        out.printf("action: periodProductList | %s ~ %s 일별가격 → 월별평균 CV%n%n", START_DAY, END_DAY);

        // 품목별 CV 계산
        Map<TargetItem, Double> itemCv = new LinkedHashMap<>();

        for (TargetItem item : TargetItem.values()) {
            List<Double> monthlyAvgs = fetchMonthlyAvgs(http, item, out);

            if (monthlyAvgs.size() < 3) {
                out.printf("  [SKIP] %s: 월별 데이터 %d개 (부족)%n", item.name, monthlyAvgs.size());
                continue;
            }

            double cv = computeCv(monthlyAvgs);
            itemCv.put(item, cv);
            out.printf("  %-18s %2d개월, CV=%.3f  (평균=%.0f)%n",
                    item.name, monthlyAvgs.size(), cv,
                    monthlyAvgs.stream().mapToDouble(d -> d).average().orElse(0));
        }
        out.println();

        // 카테고리별 집계
        out.println("=== 카테고리별 임계값 ===\n");
        Map<Category, Double> thresholds = new LinkedHashMap<>();

        for (Category cat : Category.values()) {
            if (cat == Category.PROCESSED) {
                thresholds.put(cat, 0.03);
                out.printf("[%s] 고정 3%%%n%n", cat.label);
                continue;
            }
            if (cat == Category.LIVESTOCK) {
                // periodProductList는 농산물 전용 API — 축산물 미지원 (축평원 EKAPE 별도)
                // 업계 참고값: 소고기 연간 CV ~5~15%, 돼지 ~5~10%, 닭 ~10~15%
                // → 보수적 중간값 0.08 사용
                thresholds.put(cat, 0.08);
                out.printf("[%s]%n  periodProductList 미지원 — 고정 8%% (EKAPE 별도 분석 필요)%n%n", cat.label);
                continue;
            }

            List<Double> catCvs = itemCv.entrySet().stream()
                    .filter(e -> e.getKey().category == cat)
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            out.printf("[%s]%n", cat.label);

            if (catCvs.isEmpty()) {
                out.println("  데이터 없음 → 기본값 0.10\n");
                thresholds.put(cat, 0.10);
                continue;
            }

            // n ≤ 3 이면 이상치 제거 생략 (표본 부족으로 z-score 신뢰도 낮음)
            List<Double> cleaned = catCvs.size() <= 3 ? new ArrayList<>(catCvs) : removeOutliers(catCvs);
            double threshold = cleaned.stream().mapToDouble(d -> d).average().orElse(0.10);
            thresholds.put(cat, threshold);

            out.printf("  품목 CV : %s%n", fmt(catCvs));
            if (catCvs.size() > 3) out.printf("  제거 후 : %s%n", fmt(cleaned));
            out.printf("  → threshold = %.4f (%.1f%%)%n%n", threshold, threshold * 100);
        }

        printResult(out, thresholds);
    }

    // 연도별 5회 분할 호출 — periodProductList는 호출당 최대 ~1년치 반환
    private static final String[][] YEAR_WINDOWS = {
        {"20210101", "20211231"},
        {"20220101", "20221231"},
        {"20230101", "20231231"},
        {"20240101", "20241231"},
        {"20250101", "20260428"},
    };

    // ── periodProductList 호출 → 월별 평균가 목록 반환 ───────────────────────
    // 전략:
    //   1) 최근 1년 윈도우로 유효한 attempt combo(cls/county/kind) 탐색
    //   2) 찾으면 그 combo로 5년 전체 윈도우 순회 → 월별 데이터 누적
    //   3) 최종 월별 평균 리스트 반환
    private static List<Double> fetchMonthlyAvgs(HttpClient http,
                                                  TargetItem item,
                                                  PrintStream out) throws Exception {

        // {cls, county, kindCode, gradeCode}  순서대로 시도
        String[][] attempts = item.preferRetail
            ? new String[][]{
                {"01", "1101", item.kindCode, item.gradeCode},
                {"01", "",     item.kindCode, item.gradeCode},
                {"01", "1101", "",            ""},
                {"01", "",     "",            ""},
                {"02", "1101", item.kindCode, item.gradeCode},
                {"02", "1101", "",            ""},
              }
            : new String[][]{
                {"02", "1101", item.kindCode, item.gradeCode},
                {"01", "1101", item.kindCode, item.gradeCode},
                {"01", "",     item.kindCode, item.gradeCode},
                {"01", "1101", "",            ""},
                {"02", "1101", "",            ""},
                {"01", "",     "",            ""},
              };

        // Step 1: 최근 1년으로 작동하는 combo 탐색
        String[] bestCombo = null;
        String probeStart = YEAR_WINDOWS[YEAR_WINDOWS.length - 1][0];
        String probeEnd   = YEAR_WINDOWS[YEAR_WINDOWS.length - 1][1];

        for (String[] attempt : attempts) {
            String cls = attempt[0], county = attempt[1];
            String kindCode = attempt[2], gradeCode = attempt[3];
            String url = buildUrl(cls, probeStart, probeEnd, item, kindCode, gradeCode, county);
            try {
                HttpResponse<String> res = http.send(
                        HttpRequest.newBuilder().uri(URI.create(url))
                                .timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 500) {
                    out.printf("  [WARN] %s HTTP 500%n", item.name);
                    continue;
                }
                if (res.statusCode() != 200) continue;
                Map<String, List<Long>> probe = parseToMonthlyData(res.body(), item.name, true, out);
                if (!probe.isEmpty()) {
                    bestCombo = attempt;
                    break;
                } else {
                    out.printf("  [DIAG] %s → error_code=001%n", item.name);
                }
            } catch (Exception e) {
                out.printf("  [ERR] %s: %s%n", item.name, e.getMessage());
            }
        }

        if (bestCombo == null) return List.of();

        // Step 2: 찾은 combo로 5개 연도 윈도우 전부 호출 → 누적
        String cls = bestCombo[0], county = bestCombo[1];
        String kindCode = bestCombo[2], gradeCode = bestCombo[3];

        Map<String, List<Long>> allMonthly = new LinkedHashMap<>();
        int successYears = 0;

        for (String[] yw : YEAR_WINDOWS) {
            String url = buildUrl(cls, yw[0], yw[1], item, kindCode, gradeCode, county);
            try {
                HttpResponse<String> res = http.send(
                        HttpRequest.newBuilder().uri(URI.create(url))
                                .timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) continue;
                Map<String, List<Long>> yearData = parseToMonthlyData(res.body(), item.name, true, out);
                if (!yearData.isEmpty()) {
                    allMonthly.putAll(yearData);
                    successYears++;
                }
            } catch (Exception ignored) {}
        }

        List<Double> result = allMonthly.values().stream()
                .map(prices -> prices.stream().mapToLong(v -> v).average().orElse(0))
                .filter(avg -> avg > 0)
                .collect(Collectors.toList());

        if (!result.isEmpty()) {
            String countyLabel = county.isEmpty() ? "전국" : county;
            String kindLabel   = kindCode.isEmpty() ? "kind생략" : kindCode;
            String gradeLabel  = gradeCode.isEmpty() ? "grade생략" : gradeCode;
            out.printf("  [OK cls=%s county=%s kind=%s grade=%s | %d년 %d개월] %s%n",
                    cls, countyLabel, kindLabel, gradeLabel, successYears, result.size(), item.name);
        }
        return result;
    }

    private static String buildUrl(String cls, String startDay, String endDay, TargetItem item,
                                    String kindCode, String gradeCode, String county) {
        return BASE_URL
                + "?action=periodProductList"
                + "&p_cert_key="         + API_KEY
                + "&p_cert_id="          + API_ID
                + "&p_returntype=xml"
                + "&p_productclscode="   + cls
                + "&p_startday="         + startDay
                + "&p_endday="           + endDay
                + "&p_itemcategorycode=" + item.categoryCode
                + "&p_itemcode="         + item.itemCode
                + (kindCode.isEmpty()  ? "" : "&p_kindcode="        + kindCode)
                + (gradeCode.isEmpty() ? "" : "&p_productrankcode=" + gradeCode)
                + (county.isEmpty()    ? "" : "&p_countycode="      + county)
                + "&p_convert_kg_yn=N";
    }

    // ── XML 파싱 → Map<"YYYY-MM", List<Long 일별가격>> ──────────────────────
    // silent=true 이면 에러 출력 생략 (연도별 루프 내부 호출용)
    private static Map<String, List<Long>> parseToMonthlyData(String xml, String itemName,
                                                                boolean silent, PrintStream out) {
        Map<String, List<Long>> byMonth = new LinkedHashMap<>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList errNodes = doc.getElementsByTagName("error_code");
            if (errNodes.getLength() > 0) {
                String errorCode = errNodes.item(0).getTextContent().trim();
                if (!"000".equals(errorCode)) return Map.of();
            }

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element el      = (Element) items.item(i);
                String county   = getTagText(el, "countyname");
                String yyyy     = getTagText(el, "yyyy");
                String regday   = getTagText(el, "regday");  // "MM/DD"
                String priceStr = getTagText(el, "price");

                if ("평년".equals(county)) continue;
                if (yyyy == null || regday == null) continue;
                if (!validPrice(priceStr)) continue;
                if (regday.length() < 2) continue;

                String monthKey = yyyy + "-" + regday.substring(0, 2);
                try {
                    long price = Long.parseLong(priceStr.replace(",", "").trim());
                    if (price > 0) byMonth.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(price);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            if (!silent) out.printf("  [PARSE ERROR] %s: %s%n", itemName, e.getMessage());
        }
        return byMonth;
    }

    private static boolean validPrice(String s) {
        return s != null && !s.isBlank() && !"-".equals(s.trim());
    }

    // ── 통계 ──────────────────────────────────────────────────────────────────
    private static double computeCv(List<Double> values) {
        double mean = values.stream().mapToDouble(d -> d).average().orElse(0);
        if (mean == 0) return 0;
        double var = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return Math.sqrt(var) / mean;
    }

    private static List<Double> removeOutliers(List<Double> cvs) {
        if (cvs.size() <= 2) return new ArrayList<>(cvs);
        double mean = cvs.stream().mapToDouble(d -> d).average().orElse(0);
        double std  = Math.sqrt(cvs.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));
        if (std == 0) return new ArrayList<>(cvs);
        List<Double> cleaned = cvs.stream()
                .filter(v -> Math.abs((v - mean) / std) <= ZSCORE_THRESHOLD)
                .collect(Collectors.toList());
        return cleaned.size() >= 2 ? cleaned : new ArrayList<>(cvs);
    }

    // ── 결과 출력 ─────────────────────────────────────────────────────────────
    private static void printResult(PrintStream out, Map<Category, Double> thresholds) {
        out.println("=".repeat(62));
        out.println("=== KamisCategory enum 추천값 ===");
        out.println("=".repeat(62));
        out.println();
        thresholds.forEach((cat, t) ->
                out.printf("  %-12s  threshold = %.4f  (%.1f%%)  // %s%n",
                        cat.name(), t, t * 100, cat.label));
        out.println();
        out.println("  ── 복붙용 enum 코드 ──");
        out.println("  enum KamisCategory {");
        thresholds.forEach((cat, t) ->
                out.printf("    %-12s(%.2f),  // %s%n", cat.name(), t, cat.label));
        out.println("    ;");
        out.println("    final double buySignalThreshold;");
        out.println("    KamisCategory(double t) { this.buySignalThreshold = t; }");
        out.println("  }");
        out.println();
        out.println("  buySignal 조건: currentWholesalePrice < monthAvg × (1 - threshold)");
    }

    private static String getTagText(Element el, String tag) {
        NodeList list = el.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
    }

    private static String fmt(List<Double> list) {
        return list.stream().map(v -> String.format("%.3f", v))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
