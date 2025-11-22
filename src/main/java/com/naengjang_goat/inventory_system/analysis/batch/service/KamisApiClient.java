package com.naengjang_goat.inventory_system.analysis.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naengjang_goat.inventory_system.analysis.batch.dto.KamisPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<KamisPriceDto> fetchDailySales() {
        try {
            String url = baseUrl +
                    "?action=" + URLEncoder.encode(dailyAction, StandardCharsets.UTF_8) +
                    "&p_cert_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) +
                    "&p_cert_id=" + URLEncoder.encode(apiId, StandardCharsets.UTF_8) +
                    "&p_returntype=" + returnType;

            log.info("🔗 Fetching KAMIS: {}", url);

            String response = restTemplate.getForObject(url, String.class);

            JsonNode items = mapper.readTree(response)
                    .path("data")
                    .path("item");

            List<KamisPriceDto> result = new ArrayList<>();

            for (JsonNode node : items) {
                KamisPriceDto dto = new KamisPriceDto();
                dto.setProductName(node.path("item_name").asText());
                dto.setUnit(node.path("unit").asText());
                dto.setDpr1(node.path("dpr1").asText());
                dto.setDpr2(node.path("dpr2").asText());
                dto.setDpr3(node.path("dpr3").asText());
                dto.setDpr4(node.path("dpr4").asText());
                result.add(dto);
            }

            return result;

        } catch (Exception e) {
            log.error("❌ KAMIS API ERROR", e);
            return new ArrayList<>();
        }
    }
}
