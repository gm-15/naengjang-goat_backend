package com.naengjang_goat.inventory_system.batch.service;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KamisApiClient {

    @Value("${kamis.api.base-url}")
    private String baseUrl;

    @Value("${kamis.api.key}")
    private String apiKey;

    @Value("${kamis.api.id}")
    private String apiId;

    @Value("${kamis.api.return-type}")
    private String returnType;

    @Value("${kamis.api.action.daily}")
    private String dailyAction;

    private final RestTemplate restTemplate;

    /** KAMIS 카테고리 코드 (식량작물·채소·축산·수산·조미료·과일). */
    private static final String[] CATEGORY_CODES =
            { "100", "200", "300", "400", "500", "600" };

    // KAMIS 는 dash 포맷만 응답. yyyy/MM/dd 로는 빈 응답 (HTTP 000) 발생.
    // sim, 2026-06-05 — kim 인수인계서 4섹션 버그 패치.
    private static final DateTimeFormatter KAMIS_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 주말 건너뛴 가장 최근 평일. 공휴일 판단은 API 응답에 위임. */
    private static LocalDate prevWeekday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    /**
     * 단일 카테고리 + 날짜 지정 호출.
     */
    public String fetchXml(String categoryCode, String regday) {
        try {
            String url = baseUrl +
                    "?action=" + URLEncoder.encode(dailyAction, StandardCharsets.UTF_8) +
                    "&p_cert_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) +
                    "&p_cert_id=" + URLEncoder.encode(apiId, StandardCharsets.UTF_8) +
                    "&p_returntype=" + URLEncoder.encode(returnType, StandardCharsets.UTF_8) +
                    // sim, 2026-06-05 — kim 인수인계서 4섹션 패치:
                    //   · p_product_cls_code: 01(소매) → 02(도매). 01 은 KAMIS 가 빈 응답
                    //   · p_category_code → p_item_category_code 공식 파라미터명
                    //   · p_convert_kg_yn=Y 추가 — 박스/포기 단위 → kg 환산
                    "&p_product_cls_code=02" +
                    "&p_item_category_code=" + categoryCode +
                    "&p_regday=" + regday +
                    "&p_convert_kg_yn=Y";

            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("[KAMIS-API] fetchXml error category={} regday={}", categoryCode, regday, e);
            return null;
        }
    }

    /** @deprecated fetchXml(categoryCode, regday) 사용 권장. */
    @Deprecated
    public String fetchXml(String categoryCode) {
        return fetchXml(categoryCode, prevWeekday(LocalDate.now().minusDays(1)).format(KAMIS_DATE_FMT));
    }

    /**
     * 지정 날짜 기준 6개 카테고리 수집.
     * 0건이면 공휴일로 간주하고 빈 리스트 반환 (호출자가 날짜 변경 후 재시도).
     */
    private List<KamisPriceDto> fetchForDate(LocalDate date) {
        String regday = date.format(KAMIS_DATE_FMT);
        List<KamisPriceDto> all = new ArrayList<>();
        for (String cat : CATEGORY_CODES) {
            String xml = fetchXml(cat, regday);
            if (xml == null || xml.isBlank()) continue;
            try {
                List<KamisPriceDto> parsed = parseXml(xml);
                log.info("[KAMIS-API] {} category={} parsed={}", regday, cat, parsed.size());
                all.addAll(parsed);
            } catch (Exception e) {
                log.error("[KAMIS-API] parse error category={} regday={}", cat, regday, e);
            }
        }
        return all;
    }

    /**
     * 모든 카테고리 합쳐서 DTO 리스트로 반환. Reader 가 사용.
     *
     * 날짜 결정 전략:
     *   1) 어제부터 시작, 주말은 스킵
     *   2) 0건이면 공휴일로 간주 → 하루 더 뒤로 (최대 10일)
     *   → 연휴 직후에도 가장 최근 데이터를 자동으로 수집
     */
    public List<KamisPriceDto> fetchAllCategories() {
        LocalDate candidate = prevWeekday(LocalDate.now().minusDays(1));

        for (int attempt = 1; attempt <= 10; attempt++) {
            log.info("[KAMIS-API] 조회 기준일: {} ({}) [{}회차]",
                    candidate.format(KAMIS_DATE_FMT), candidate.getDayOfWeek(), attempt);

            List<KamisPriceDto> result = fetchForDate(candidate);

            if (!result.isEmpty()) {
                log.info("[KAMIS-API] total parsed: {} ({})", result.size(), candidate.format(KAMIS_DATE_FMT));
                return result;
            }

            log.info("[KAMIS-API] {} 데이터 없음 (공휴일/미게시) → 이전 평일 재시도", candidate.format(KAMIS_DATE_FMT));
            candidate = prevWeekday(candidate.minusDays(1));
        }

        log.warn("[KAMIS-API] 10일 이내 데이터 없음 — 빈 리스트 반환");
        return List.of();
    }

    /** @deprecated 단일 카테고리만 받던 옛 메서드. fetchAllCategories() 사용 권장. */
    @Deprecated
    public String fetchXml() {
        return fetchXml("100");
    }

    public List<KamisPriceDto> fetchDailySales() {
        try {
            String xml = fetchXml();
            if (xml == null || xml.isBlank()) return new ArrayList<>();

            List<KamisPriceDto> parsed = parseXml(xml);
            log.info("[KAMIS-API] parsed {} items", parsed.size());
            return parsed;
        } catch (Exception e) {
            log.error("[KAMIS-API] error while fetching daily sales", e);
            return new ArrayList<>();
        }
    }

    private List<KamisPriceDto> parseXml(String xml) throws Exception {
        List<KamisPriceDto> list = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));

        NodeList dataNodes = document.getElementsByTagName("data");
        if (dataNodes.getLength() == 0) return list;

        Element data = (Element) dataNodes.item(0);
        String error = getTagText(data, "error_code");
        if (!"000".equals(error)) {
            log.warn("[KAMIS-API] error_code={} (비정상 응답, 빈 리스트 반환)", error);
            return list;
        }

        NodeList items = data.getElementsByTagName("item");

        for (int i = 0; i < items.getLength(); i++) {
            Element e = (Element) items.item(i);

            KamisPriceDto dto = new KamisPriceDto();
            dto.setItemCode(getTagText(e, "item_code"));
            dto.setProductName(getTagText(e, "item_name"));
            dto.setUnit(getTagText(e, "unit"));
            dto.setDpr1(normalize(getTagText(e, "dpr1")));
            dto.setDpr4(normalize(getTagText(e, "dpr4")));

            if (isBlank(dto.getProductName())) continue;

            list.add(dto);
        }

        return list;
    }

    private String getTagText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent().trim();
    }

    private String normalize(String v) {
        if (v == null || v.isBlank() || v.equals("-")) return null;
        return v.replace(",", "").trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
