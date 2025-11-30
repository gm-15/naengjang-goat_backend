package com.naengjang_goat.inventory_system.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
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

            String response = restTemplate.getForObject(url, String.class);

            // ★★★★★ RAW 전체 응답 출력 — 반드시 확인해야 함
            log.error("KAMIS RAW RESPONSE = {}", response);

            JsonNode root = mapper.readTree(response);

            // JSON 구조 파악 전까지 임시로 빈 리스트 리턴
            List<KamisPriceDto> list = new ArrayList<>();

            JsonNode items = root.path("data").path("item");

            for (JsonNode n : items) {
                KamisPriceDto dto = new KamisPriceDto();
                dto.setProductName(n.path("item_name").asText());
                dto.setUnit(n.path("unit").asText());
                dto.setDpr1(n.path("dpr1").asText());
                dto.setDpr4(n.path("dpr4").asText());
                list.add(dto);
            }

            return list;

        } catch (Exception e) {
            log.error("KAMIS API ERROR", e);
            return new ArrayList<>();
        }
    }
}
