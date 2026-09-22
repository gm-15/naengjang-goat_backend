package com.naengjang_goat.inventory_system.batch.ekape;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EKAPE 축산물 소비자가격 공공데이터포털 정식 OpenAPI 클라이언트.
 *
 * 서비스: 축산물품질평가원_축산물유통정보 (data.go.kr/data/15000577)
 * 오퍼레이션: consumerPriceDaily (일자별 축산물소비자가격 정보)
 *
 * 요청 파라미터:
 *   serviceKey  : 공공데이터포털 인증키
 *   standYmd    : 기준일자 (yyyyMMdd)
 *   judgeKind   : 축종코드 (4301 소, 4304 돼지, 9901 닭, 9903 계란 등)
 *   itemCd      : 품목코드 (닭·계란은 생략 가능)
 *
 * 축종/품목 코드 (활용가이드 v2.9):
 *   [소       4301] 21 안심, 22 등심, 36 설도, 40 양지, 50 갈비   (등급 응답: 1++ / 1+ / 1 / 2 / 3)
 *   [돼지     4304] 25 앞다리, 27 삼겹살, 28 갈비, 68 목살        (등급 없음: '구분없음')
 *   [수입쇠고기 4401] 31 갈비(냉동), 37 갈비살(냉장)              (grdNm 에 국가명: 미국산 등)
 *   [수입돼지   4402] 27 삼겹살                                    (등급 없음)
 *   [닭       9901] itemCd 생략 → 육계(kg)                        (등급 없음)
 *   [계란     9903] itemCd 생략 → XL30구(일반란)                  (등급 없음)
 *
 * 응답 단위:
 *   소·돼지 = 원/100g
 *   닭      = 원/kg
 *   계란    = 원/30구  ← 다른 축종과 다름. 소비처에서 unit 확인 필수
 *
 * 응답 특이사항:
 *   - 한 응답에 실제 날짜 데이터 + standYmd='평년' 데이터가 함께 옴 → 평년은 필터링
 *   - 소는 등급별로 여러 item 반환 → 대표 등급(1등급)만 선택
 *
 * 2026-09-22 sim — 기존 HTML 파싱(ekapepia.com) 방식에서 정식 OpenAPI 로 전환.
 *   전환 배경: EKAPE 사이트가 '다봄' 으로 리브랜딩되며 기존 endpoint 정지 (2026-09-18 확인).
 */
@Slf4j
@Service
public class EkapeApiClient {

    private static final DateTimeFormatter YMD_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 소 등급 선택 필터 (여러 등급 중 대표값). */
    private static final String CATTLE_GRADE_FILTER = "1등급";

    /** 수입 쇠고기 국가 필터 (한국 수입 쇠고기 중 최다인 미국산 기준). */
    private static final String IMPORT_BEEF_ORIGIN_FILTER = "미국산";

    /** 응답에 함께 오는 평년(장기평균) 데이터 표기. skip 대상. */
    private static final String YEAR_AVG_MARKER = "평년";

    private final String baseUrl;
    private final String serviceKey;
    private final RestTemplate restTemplate;
    private final DocumentBuilderFactory xmlFactory;

    public EkapeApiClient(RestTemplate restTemplate,
                          @Value("${ekape.api.base-url}") String baseUrl,
                          @Value("${ekape.api.key}") String serviceKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
        this.xmlFactory = DocumentBuilderFactory.newInstance();
    }

    /** EKAPE 재료 파라미터 정의. */
    public record EkapeProduct(
            String name,
            String judgeKind,
            String itemCd,
            String unit,
            String gradeFilter
    ) {}

    /**
     * 재료명 → EKAPE 파라미터 매핑.
     * LinkedHashMap 순서 = 우선순위 (구체적 키워드 먼저).
     */
    private static final List<Map.Entry<String, EkapeProduct>> KEYWORD_LIST;
    static {
        List<Map.Entry<String, EkapeProduct>> list = new ArrayList<>();
        // ── 수입 (국산보다 먼저 배치. "수입삼겹살" 이 "삼겹살" 국산 매핑에 잡히기 전에 우선 매칭) ──
        list.add(Map.entry("수입갈비살", new EkapeProduct("수입쇠고기 갈비살(냉장)", "4401", "37", "100g", IMPORT_BEEF_ORIGIN_FILTER)));
        list.add(Map.entry("수입갈비",   new EkapeProduct("수입쇠고기 갈비(냉동)",   "4401", "31", "100g", IMPORT_BEEF_ORIGIN_FILTER)));
        list.add(Map.entry("수입삼겹살", new EkapeProduct("수입돼지 삼겹살",         "4402", "27", "100g", null)));
        list.add(Map.entry("수입쇠고기", new EkapeProduct("수입쇠고기 갈비(냉동)",   "4401", "31", "100g", IMPORT_BEEF_ORIGIN_FILTER)));
        list.add(Map.entry("수입돼지",   new EkapeProduct("수입돼지 삼겹살",         "4402", "27", "100g", null)));
        // ── 국산 돼지 (등급 없음) ──
        list.add(Map.entry("삼겹살",   new EkapeProduct("돼지 삼겹살", "4304", "27", "100g", null)));
        list.add(Map.entry("목살",     new EkapeProduct("돼지 목살",   "4304", "68", "100g", null)));
        list.add(Map.entry("목심",     new EkapeProduct("돼지 목살",   "4304", "68", "100g", null)));
        list.add(Map.entry("앞다리",   new EkapeProduct("돼지 앞다리", "4304", "25", "100g", null)));
        list.add(Map.entry("돼지갈비", new EkapeProduct("돼지 갈비",   "4304", "28", "100g", null)));
        // ── 국산 소 (등급 필터: 1등급 대표) ──
        list.add(Map.entry("등심",     new EkapeProduct("소 등심",     "4301", "22", "100g", CATTLE_GRADE_FILTER)));
        list.add(Map.entry("안심",     new EkapeProduct("소 안심",     "4301", "21", "100g", CATTLE_GRADE_FILTER)));
        list.add(Map.entry("양지",     new EkapeProduct("소 양지",     "4301", "40", "100g", CATTLE_GRADE_FILTER)));
        list.add(Map.entry("설도",     new EkapeProduct("소 설도",     "4301", "36", "100g", CATTLE_GRADE_FILTER)));
        list.add(Map.entry("갈비",     new EkapeProduct("소 갈비",     "4301", "50", "100g", CATTLE_GRADE_FILTER)));
        // ── 닭 (itemCd 생략 → 육계kg) ──
        list.add(Map.entry("닭",       new EkapeProduct("닭 육계",     "9901", null, "kg",   null)));
        // ── 계란 (itemCd 생략 → XL30구, 단위 원/30구) ──
        list.add(Map.entry("계란",     new EkapeProduct("계란 XL30구", "9903", null, "30구", null)));
        list.add(Map.entry("달걀",     new EkapeProduct("계란 XL30구", "9903", null, "30구", null)));
        // ── 대체 키워드 (fallback) ──
        list.add(Map.entry("돼지고기", new EkapeProduct("돼지 삼겹살", "4304", "27", "100g", null)));
        list.add(Map.entry("소고기",   new EkapeProduct("소 등심",     "4301", "22", "100g", CATTLE_GRADE_FILTER)));
        list.add(Map.entry("쇠고기",   new EkapeProduct("소 등심",     "4301", "22", "100g", CATTLE_GRADE_FILTER)));
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
     * 기간 내 일별 가격 조회.
     *
     * 정식 API 는 standYmd 단일 파라미터만 지원하므로 날짜별로 반복 호출한다.
     *
     * @return 날짜 → 평균가격(ntslPrc, 정수). 응답 없거나 파싱 실패 시 빈 맵.
     */
    public Map<LocalDate, Integer> fetchDailyPrices(EkapeProduct product, LocalDate from, LocalDate to) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("[EKAPE-API] serviceKey 미설정 — 호출 skip");
            return Map.of();
        }

        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Integer price = fetchSingleDay(product, cursor);
            if (price != null) {
                result.put(cursor, price);
            }
            cursor = cursor.plusDays(1);
        }

        log.info("[EKAPE-API] {} judgeKind={} itemCd={} → {}건 수집 ({}~{})",
                product.name(), product.judgeKind(), product.itemCd(),
                result.size(), from, to);
        return result;
    }

    // ─── private ────────────────────────────────────────────────────────────

    /** 단일 날짜 API 호출 → 평균가격 반환. null = 데이터 없음/오류. */
    private Integer fetchSingleDay(EkapeProduct product, LocalDate date) {
        String url = buildUrl(product, date);
        String xml = fetchXml(url);
        if (xml == null) return null;
        return parseAveragePrice(xml, product);
    }

    private String buildUrl(EkapeProduct product, LocalDate date) {
        StringBuilder sb = new StringBuilder(baseUrl)
                .append("?serviceKey=").append(URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))
                .append("&standYmd=").append(date.format(YMD_FMT))
                .append("&judgeKind=").append(product.judgeKind());
        if (product.itemCd() != null && !product.itemCd().isBlank()) {
            sb.append("&itemCd=").append(product.itemCd());
        }
        return sb.toString();
    }

    private String fetchXml(String url) {
        try {
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) {
                log.warn("[EKAPE-API] 빈 응답 url={}", url);
                return null;
            }
            return xml;
        } catch (Exception e) {
            log.error("[EKAPE-API] HTTP 오류 url={}", url, e);
            return null;
        }
    }

    /**
     * XML 응답 파싱 → 평균가격(ntslPrc) 추출.
     * - standYmd='평년' 항목은 skip
     * - 소는 gradeFilter 와 일치하는 grdNm 만 선택
     * - 여러 후보 중 첫 번째 사용
     */
    private Integer parseAveragePrice(String xml, EkapeProduct product) {
        try {
            DocumentBuilder builder = xmlFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            String resultCode = getSingleTagText(doc, "resultCode");
            if (!"00".equals(resultCode)) {
                String msg = getSingleTagText(doc, "resultMsg");
                log.warn("[EKAPE-API] 비정상 응답 code={} msg={}", resultCode, msg);
                return null;
            }

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String standYmd = getTagText(item, "standYmd");
                if (YEAR_AVG_MARKER.equals(standYmd)) continue;

                if (product.gradeFilter() != null) {
                    String grdNm = getTagText(item, "grdNm");
                    if (!product.gradeFilter().equals(grdNm)) continue;
                }

                String ntslPrc = getTagText(item, "ntslPrc");
                if (ntslPrc == null || ntslPrc.isBlank()) continue;
                return Integer.parseInt(ntslPrc.trim());
            }
        } catch (Exception e) {
            log.error("[EKAPE-API] XML 파싱 오류", e);
        }
        return null;
    }

    private String getTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    private String getSingleTagText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }
}
