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
import java.time.LocalDate;
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

    public String fetchXml() {
        try {
            String url = baseUrl +
                    "?action=" + URLEncoder.encode(dailyAction, StandardCharsets.UTF_8) +
                    "&p_cert_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) +
                    "&p_cert_id=" + URLEncoder.encode(apiId, StandardCharsets.UTF_8) +
                    "&p_returntype=" + URLEncoder.encode(returnType, StandardCharsets.UTF_8) +
                    "&p_product_cls_code=02" +
                    "&p_category_code=100" +
                    "&p_regday=" + LocalDate.now().minusDays(365);

            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("[KAMIS-API] fetchXml error", e);
            return null;
        }
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
        if (!"000".equals(error)) return list;

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
