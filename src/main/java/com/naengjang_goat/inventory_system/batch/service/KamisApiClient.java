package com.naengjang_goat.inventory_system.batch.service;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class KamisApiClient {
    @Value("${kamis.api.base-url}") private String baseUrl;
    @Value("${kamis.api.key}") private String apiKey;
    @Value("${kamis.api.id}") private String apiId;
    @Value("${kamis.api.return-type}") private String returnType;
    @Value("${kamis.api.action.daily}") private String dailyAction;
    @Value("${kamis.api.product-class:02}") private String productClass;
    private final RestTemplate restTemplate;
    private final Clock clock;
    private static final String[] CATEGORIES = {"100", "200", "300", "400", "500", "600"};
    private static final Pattern ABSOLUTE_DATE = Pattern.compile("(?:(\\d{4})[-/])?(\\d{1,2})[-/](\\d{1,2})");

    private static LocalDate prevWeekday(LocalDate date) {
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)
            date = date.minusDays(1);
        return date;
    }

    public String fetchXml(String category, String regday) {
        if (!List.of("01", "02").contains(productClass))
            throw new IllegalStateException("KAMIS product-class must be 01 or 02");
        String url = baseUrl + "?action=" + encode(dailyAction)
                + "&p_cert_key=" + encode(apiKey) + "&p_cert_id=" + encode(apiId)
                + "&p_returntype=" + encode(returnType) + "&p_product_cls_code=" + productClass
                + "&p_item_category_code=" + category + "&p_convert_kg_yn=Y&p_regday=" + regday;
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (Exception failure) {
            // RestTemplate 예외의 전체 URL에는 인증 키가 들어갈 수 있다.
            throw new IllegalStateException("KAMIS 접근 실패: category=" + category
                    + " date=" + regday + " type=" + failure.getClass().getSimpleName());
        }
    }

    private List<KamisPriceDto> fetchForDate(LocalDate date) {
        var all = new ArrayList<KamisPriceDto>();
        for (String category : CATEGORIES) {
            String xml = fetchXml(category, date.toString());
            if (xml == null || xml.isBlank()) continue;
            var parsed = parseXml(xml, date);
            log.info("[KAMIS-API] date={} category={} usable={}", date, category, parsed.size());
            all.addAll(parsed);
        }
        return all;
    }

    public List<KamisPriceDto> fetchAllCategories() {
        LocalDate candidate = prevWeekday(LocalDate.now(clock).minusDays(1));
        for (int attempt = 0; attempt < 10; attempt++) {
            var result = fetchForDate(candidate);
            if (!result.isEmpty()) return result;
            candidate = prevWeekday(candidate.minusDays(1));
        }
        throw new IllegalStateException("KAMIS: 최근 10평일에 날짜를 확인할 수 있는 가격 자료가 없습니다");
    }

    private List<KamisPriceDto> parseXml(String xml, LocalDate requested) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var nodes = document.getElementsByTagName("data");
            if (nodes.getLength() == 0) throw new IllegalStateException("KAMIS 응답에 data가 없습니다");
            var data = (Element) nodes.item(0);
            String error = text(data, "error_code");
            if ("200".equals(error)) return List.of();
            if (!"000".equals(error)) throw new IllegalStateException("KAMIS 응답 오류: " + error);
            var items = data.getElementsByTagName("item");
            var result = new ArrayList<KamisPriceDto>();
            for (int i = 0; i < items.getLength(); i++) {
                var item = (Element) items.item(i);
                var dto = new KamisPriceDto();
                dto.setItemCode(text(item, "item_code"));
                dto.setProductName(text(item, "item_name"));
                if (dto.getProductName() == null || dto.getProductName().isBlank()) continue;
                dto.setKindCode(text(item, "kind_code"));
                dto.setKindName(text(item, "kind_name"));
                dto.setRankCode(text(item, "rank_code"));
                dto.setGrade(text(item, "rank"));
                dto.setUnit("kg");
                dto.setPriceBasis("02".equals(productClass) ? "WHOLESALE" : "RETAIL");
                dto.setRequestedDate(requested);
                dto.setDpr1(price(text(item, "dpr1")));
                dto.setDpr2(price(text(item, "dpr2")));
                dto.setDpr4(price(text(item, "dpr4")));
                LocalDate firstDate = absoluteDate(text(item, "day1"), requested);
                if (dto.getDpr1() != null) {
                    dto.setCurrentPrice(dto.getDpr1());
                    dto.setObservedDate(firstDate == null ? requested : firstDate);
                } else {
                    // '1일전'을 달력 하루 전이라고 추측하지 않는다. 실제 날짜가 있어야 대체 가능.
                    LocalDate previous = absoluteDate(text(item, "day2"), requested);
                    if (dto.getDpr2() == null || previous == null) continue;
                    dto.setCurrentPrice(dto.getDpr2());
                    dto.setObservedDate(previous);
                }
                if (!dto.getObservedDate().isAfter(requested)) result.add(dto);
            }
            return result;
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("KAMIS XML 해석 실패: " + failure.getClass().getSimpleName());
        }
    }

    private LocalDate absoluteDate(String value, LocalDate requested) {
        if (value == null) return null;
        var match = ABSOLUTE_DATE.matcher(value);
        if (!match.find()) return null;
        try {
            int year = match.group(1) == null ? requested.getYear() : Integer.parseInt(match.group(1));
            LocalDate date = LocalDate.of(year, Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)));
            if (match.group(1) == null && date.isAfter(requested)) date = date.minusYears(1);
            return date;
        } catch (RuntimeException failure) { return null; }
    }

    private String text(Element parent, String name) {
        var nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private String price(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return null;
        try {
            var amount = new BigDecimal(value.replace(",", "").trim());
            return amount.signum() > 0 ? amount.stripTrailingZeros().toPlainString() : null;
        } catch (NumberFormatException failure) { return null; }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    @Deprecated public String fetchXml(String category) {
        return fetchXml(category, prevWeekday(LocalDate.now(clock).minusDays(1)).format(DateTimeFormatter.ISO_DATE));
    }
    @Deprecated public String fetchXml() { return fetchXml("100"); }
    @Deprecated public List<KamisPriceDto> fetchDailySales() { return fetchAllCategories(); }
}
