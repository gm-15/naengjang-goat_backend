package com.naengjang_goat.inventory_system.shopping.client;

import com.naengjang_goat.inventory_system.shopping.dto.NaverShoppingItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 네이버 쇼핑 검색 API 클라이언트
 * 재료명으로 상품 검색 → 최저가 + 구매 링크 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverShoppingClient {

    @Value("${naver.shopping.base-url}")
    private String baseUrl;

    @Value("${naver.shopping.client-id}")
    private String clientId;

    @Value("${naver.shopping.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    /**
     * 재료명으로 네이버 쇼핑 검색
     *
     * @param query   검색어 (재료명)
     * @param display 결과 수 (최대 100)
     * @return 상품 목록 (최저가 순 정렬)
     */
    public List<NaverShoppingItemDto> search(String query, int display) {
        try {
            // URI 템플릿 변수 방식: RestTemplate이 한글 등 비ASCII 문자를 자동 인코딩
            String urlTemplate = baseUrl + "?query={query}&display={display}&sort=sim";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    urlTemplate, HttpMethod.GET, entity, Map.class,
                    Map.of("query", query, "display", display));

            return parseItems(response.getBody());

        } catch (Exception e) {
            log.error("[NAVER-SHOPPING] 검색 실패 query={}", query, e);
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<NaverShoppingItemDto> parseItems(Map<?, ?> body) {
        List<NaverShoppingItemDto> result = new ArrayList<>();
        if (body == null) return result;

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null) return result;

        for (Map<String, Object> item : items) {
            NaverShoppingItemDto dto = new NaverShoppingItemDto();
            dto.setTitle(stripHtml((String) item.get("title")));
            dto.setLink((String) item.get("link"));
            dto.setImage((String) item.get("image"));
            dto.setLprice((String) item.get("lprice"));
            dto.setHprice((String) item.get("hprice"));
            dto.setMallName((String) item.get("mallName"));
            dto.setBrand((String) item.get("brand"));
            dto.setMaker((String) item.get("maker"));
            dto.setCategory1((String) item.get("category1"));
            dto.setCategory2((String) item.get("category2"));
            result.add(dto);
        }

        return result;
    }

    /** 네이버 API가 title에 <b> 태그를 포함해 반환하므로 제거 */
    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "");
    }
}
