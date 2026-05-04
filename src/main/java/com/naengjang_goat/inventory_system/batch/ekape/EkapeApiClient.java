package com.naengjang_goat.inventory_system.batch.ekape;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EKAPE(축산물품질평가원 ekapepia.com) 소비자가격 HTML 파싱 클라이언트.
 *
 * 대상 URL:
 *   https://www.ekapepia.com/v3/price/consumer/periodPrice/excel.do
 *   ?livestockType=4304&spec=27&aggregationUnit=DAY&startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
 *
 * 파라미터 출처 (2026-05-04 실측):
 *   소(4301): 안심=21, 등심=22, 설도=36, 양지=40, 갈비=50 / grade=03(1등급)
 *   돼지(4304): 앞다리=25, 삼겹살=27, 갈비=28, 목심=68 / grade 없음
 *   닭(9901): 육계(kg)=99 / grade 없음 / 단위=원/kg
 *
 * 반환 단위:
 *   소·돼지 = 원/100g, 닭 육계(kg) = 원/kg
 *   → 모두 retailPrice에 저장. unit 컬럼으로 구분 가능.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EkapeApiClient {

    private static final String BASE_URL =
            "https://www.ekapepia.com/v3/price/consumer/periodPrice/excel.do";
    private static final DateTimeFormatter EKAPE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 일별 응답 날짜 헤더: "05월 03일"
    private static final Pattern DAY_HEADER = Pattern.compile("(\\d{2})월\\s*(\\d{2})일");
    // 가격 셀 내 숫자 (콤마 포함): "2,811" 또는 "11,234"
    private static final Pattern PRICE_NUM = Pattern.compile("(\\d{1,2},\\d{3})");

    private final RestTemplate restTemplate;

    /** EKAPE 재료 파라미터 정의 */
    public record EkapeProduct(String name, String livestockType, String spec, String grade, String unit) {}

    /**
     * 재료명 → EKAPE 파라미터 매핑.
     * LinkedHashMap 순서 = 우선순위 (구체적 키워드 먼저).
     */
    private static final List<Map.Entry<String, EkapeProduct>> KEYWORD_LIST;
    static {
        List<Map.Entry<String, EkapeProduct>> list = new ArrayList<>();
        list.add(Map.entry("삼겹살",  new EkapeProduct("돼지 삼겹살", "4304", "27", "",   "100g")));
        list.add(Map.entry("목심",    new EkapeProduct("돼지 목심",   "4304", "68", "",   "100g")));
        list.add(Map.entry("앞다리",  new EkapeProduct("돼지 앞다리", "4304", "25", "",   "100g")));
        list.add(Map.entry("돼지갈비",new EkapeProduct("돼지 갈비",   "4304", "28", "",   "100g")));
        list.add(Map.entry("등심",    new EkapeProduct("소 등심",     "4301", "22", "03", "100g")));
        list.add(Map.entry("안심",    new EkapeProduct("소 안심",     "4301", "21", "03", "100g")));
        list.add(Map.entry("양지",    new EkapeProduct("소 양지",     "4301", "40", "03", "100g")));
        list.add(Map.entry("설도",    new EkapeProduct("소 설도",     "4301", "36", "03", "100g")));
        list.add(Map.entry("갈비",    new EkapeProduct("소 갈비",     "4301", "50", "03", "100g")));
        list.add(Map.entry("닭",      new EkapeProduct("닭 육계",     "9901", "99", "",   "kg"  )));
        list.add(Map.entry("돼지고기",new EkapeProduct("돼지 삼겹살", "4304", "27", "",   "100g")));
        list.add(Map.entry("소고기",  new EkapeProduct("소 등심",     "4301", "22", "03", "100g")));
        list.add(Map.entry("쇠고기",  new EkapeProduct("소 등심",     "4301", "22", "03", "100g")));
        KEYWORD_LIST = list;
    }

    /** 재료명으로 EKAPE 파라미터 탐색 (첫 번째 매칭 키워드 우선). */
    public Optional<EkapeProduct> resolveProduct(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) return Optional.empty();
        return KEYWORD_LIST.stream()
                .filter(e -> ingredientName.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * EKAPE 소비자가격 일별 데이터 조회.
     *
     * @param product  EKAPE 파라미터 (livestockType, spec, grade)
     * @param from     조회 시작일
     * @param to       조회 종료일
     * @return 날짜 → 전국 평균 가격(정수, 원 단위) 맵. 파싱 실패 시 빈 맵.
     */
    public Map<LocalDate, Integer> fetchDailyPrices(EkapeProduct product, LocalDate from, LocalDate to) {
        String url = buildUrl(product, from, to, "DAY");
        String html = fetchHtml(url);
        if (html == null) return Map.of();

        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        parseTableRows(html, from, result);

        log.info("[EKAPE] {} {} {} → {}건 파싱 ({}~{})",
                product.name(), product.livestockType(), product.spec(),
                result.size(), from, to);
        return result;
    }

    // ─── private ────────────────────────────────────────────────────────────

    private String buildUrl(EkapeProduct p, LocalDate from, LocalDate to, String unit) {
        StringBuilder sb = new StringBuilder(BASE_URL)
                .append("?livestockType=").append(p.livestockType())
                .append("&startDate=").append(from.format(EKAPE_DATE_FMT))
                .append("&endDate=").append(to.format(EKAPE_DATE_FMT))
                .append("&spec=").append(p.spec())
                .append("&aggregationUnit=").append(unit);
        if (!p.grade().isBlank()) sb.append("&grade=").append(p.grade());
        return sb.toString();
    }

    private String fetchHtml(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null || html.isBlank() || html.contains("errorV3")) {
                log.warn("[EKAPE] 빈/오류 응답 url={}", url);
                return null;
            }
            return html;
        } catch (Exception e) {
            log.error("[EKAPE] HTTP 오류 url={}", url, e);
            return null;
        }
    }

    /**
     * HTML 테이블에서 날짜-가격 추출.
     *
     * 일별 응답 헤더 형식: "05월 03일" (년도 없음)
     * → from 파라미터의 연도 기준으로 LocalDate 구성.
     *   단, 12월→1월 경계는 월 감소로 감지해 연도 +1.
     */
    private void parseTableRows(String html, LocalDate fromDate, Map<LocalDate, Integer> result) {
        String[] rows = html.split("<tr>");
        int year = fromDate.getYear();
        int prevMonth = 0;

        for (String row : rows) {
            Matcher headerMatcher = DAY_HEADER.matcher(row);
            if (!headerMatcher.find()) continue;

            int month = Integer.parseInt(headerMatcher.group(1));
            int day   = Integer.parseInt(headerMatcher.group(2));

            // 년도 경계 처리: 12월 → 1월 이면 연도 +1
            if (prevMonth == 12 && month == 1) year++;
            prevMonth = month;

            LocalDate date;
            try {
                date = LocalDate.of(year, month, day);
            } catch (Exception e) {
                continue; // 잘못된 날짜 (전년/평년 행 등) 무시
            }

            // 첫 번째 <td> 내 숫자 = 전국 평균 가격
            int tdStart = row.indexOf("<td>");
            if (tdStart < 0) continue;
            int tdEnd = row.indexOf("</td>", tdStart);
            if (tdEnd < 0) continue;

            String tdContent = row.substring(tdStart, tdEnd);
            Matcher priceMatcher = PRICE_NUM.matcher(tdContent);
            if (!priceMatcher.find()) continue;

            int price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
            result.put(date, price);
        }
    }
}
