package com.naengjang_goat.inventory_system.batch.ekape;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EKAPE 공공데이터포털 OpenAPI 클라이언트 (소비자가격 일자별).
 *
 * 과거 HTML 스크래핑 방식(ekapepia.com/v3/.../excel.do) 은
 * 2026-10-16 이후 JSESSIONID 봇차단으로 수집 중단 → OpenAPI 로 전환.
 *
 * 엔드포인트:
 *   http://data.ekape.or.kr/openapi-data/service/user/grade/consumerPriceDaily
 *
 * 파라미터:
 *   serviceKey, standYmd(YYYYMMDD), judgeKind(4301 소/4304 돼지/9901 닭), itemCd
 *
 * 응답: XML. 축종·품목별 전국 평균 ntslPrc + 지역별(regionPrc1~17) + 등급(grdNm).
 *   standYmd="평년" 인 비교값 item 은 저장 제외.
 *
 * 단위:
 *   소·돼지 = 원/100g (API 그대로)
 *   닭 육계 = 원/kg
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EkapeApiClient {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${ekape.api.base-url}")
    private String baseUrl;

    @Value("${ekape.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /** EKAPE 재료 파라미터 정의 */
    public record EkapeProduct(String name, String livestockType, String spec, String grade, String unit) {}

    /**
     * 재료명 → EKAPE 파라미터 매핑.
     * LinkedHashMap 순서 = 우선순위 (구체적 키워드 먼저).
     * 과거 HTML 스크래핑 코드와 동일 매핑 유지 (API 전환 영향 없음).
     */
    private static final List<Map.Entry<String, EkapeProduct>> KEYWORD_LIST;
    static {
        List<Map.Entry<String, EkapeProduct>> list = new ArrayList<>();
        list.add(Map.entry("삼겹살",   new EkapeProduct("돼지 삼겹살", "4304", "27", "",   "100g")));
        list.add(Map.entry("목심",     new EkapeProduct("돼지 목심",   "4304", "68", "",   "100g")));
        list.add(Map.entry("앞다리",   new EkapeProduct("돼지 앞다리", "4304", "25", "",   "100g")));
        list.add(Map.entry("돼지갈비", new EkapeProduct("돼지 갈비",   "4304", "28", "",   "100g")));
        list.add(Map.entry("등심",     new EkapeProduct("소 등심",     "4301", "22", "03", "100g")));
        list.add(Map.entry("안심",     new EkapeProduct("소 안심",     "4301", "21", "03", "100g")));
        list.add(Map.entry("양지",     new EkapeProduct("소 양지",     "4301", "40", "03", "100g")));
        list.add(Map.entry("설도",     new EkapeProduct("소 설도",     "4301", "36", "03", "100g")));
        list.add(Map.entry("갈비",     new EkapeProduct("소 갈비",     "4301", "50", "03", "100g")));
        list.add(Map.entry("닭",       new EkapeProduct("닭 육계",     "9901", "99", "",   "kg"  )));
        list.add(Map.entry("돼지고기", new EkapeProduct("돼지 삼겹살", "4304", "27", "",   "100g")));
        list.add(Map.entry("소고기",   new EkapeProduct("소 등심",     "4301", "22", "03", "100g")));
        list.add(Map.entry("쇠고기",   new EkapeProduct("소 등심",     "4301", "22", "03", "100g")));
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
     * EKAPE OpenAPI 로 날짜 범위 소비자가격 수집.
     * 일자별 엔드포인트는 standYmd 1개만 받으므로 날짜마다 호출 (하루 1회).
     *
     * @param product  EKAPE 파라미터
     * @param from     조회 시작일 (포함)
     * @param to       조회 종료일 (포함)
     * @return 날짜 → 전국 평균 가격(원) 맵. 응답 없거나 평년값만 있는 날짜는 스킵.
     */
    public Map<LocalDate, Integer> fetchDailyPrices(EkapeProduct product, LocalDate from, LocalDate to) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EKAPE] ekape.api.key 미설정 — 수집 스킵");
            return Map.of();
        }

        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        int httpOk = 0, parseOk = 0;
        int totalDays = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            String xml = fetchXml(product, date);
            if (xml == null) continue;
            httpOk++;
            Integer price = parseAveragePrice(xml, date);
            if (price != null) {
                result.put(date, price);
                parseOk++;
            }
        }
        log.info("[EKAPE] {} {}·{} → {}일 요청, 응답 {}건, 가격 {}건 ({}~{})",
                product.name(), product.livestockType(), product.spec(),
                totalDays, httpOk, parseOk, from, to);
        return result;
    }

    private String fetchXml(EkapeProduct p, LocalDate date) {
        // _type=xml: RestTemplate 의 Accept 헤더에 따라 서버가 JSON 반환하는 걸 방지
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("serviceKey", apiKey)
                .queryParam("standYmd", date.format(YMD))
                .queryParam("judgeKind", p.livestockType())
                .queryParam("itemCd", p.spec())
                .queryParam("_type", "xml")
                .build(false)
                .toUriString();
        try {
            // 응답을 바이트로 받아 UTF-8 명시 디코딩 (기본 StringHttpMessageConverter 가
            // Content-Type 에 charset 이 없으면 ISO-8859-1 로 디코딩해 XML 프롤로그가 깨짐)
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) return null;
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[EKAPE] HTTP 오류 date={} {}: {}", date, p.name(), e.getMessage());
            return null;
        }
    }

    /**
     * XML 응답에서 요청 날짜와 일치하는 첫 번째 item 의 ntslPrc 반환.
     * - standYmd="평년" item 은 비교값이므로 스킵
     * - 소(4301) 는 1+등급/1등급 중 첫 번째(1+등급) 선택 (기존 HTML 스크래핑과 동일 정책)
     */
    private Integer parseAveragePrice(String xml, LocalDate date) {
        String ymd = date.format(YMD);
        // 선행 BOM / 공백 / 비-XML 문자 제거 ("Content is not allowed in prolog" 방지)
        int lt = xml.indexOf('<');
        if (lt < 0) {
            log.warn("[EKAPE] 응답에 '<' 없음 date={} head={}", date,
                    xml.substring(0, Math.min(60, xml.length())));
            return null;
        }
        if (lt > 0) {
            log.debug("[EKAPE] 선행 {} 바이트 제거 date={}", lt, date);
            xml = xml.substring(lt);
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList rcNodes = doc.getElementsByTagName("resultCode");
            if (rcNodes.getLength() > 0 && !"00".equals(rcNodes.item(0).getTextContent())) {
                NodeList msgNodes = doc.getElementsByTagName("resultMsg");
                String msg = msgNodes.getLength() > 0 ? msgNodes.item(0).getTextContent() : "";
                log.warn("[EKAPE] 에러 응답 date={}: {}", date, msg);
                return null;
            }

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String standYmd = text(item, "standYmd");
                if (!ymd.equals(standYmd)) continue;
                String ntsl = text(item, "ntslPrc");
                if (ntsl == null || ntsl.isBlank()) continue;
                try {
                    return Integer.parseInt(ntsl.trim());
                } catch (NumberFormatException ignore) {
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[EKAPE] XML 파싱 실패 date={}: {}", date, e.getMessage());
            return null;
        }
    }

    private String text(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : null;
    }
}
