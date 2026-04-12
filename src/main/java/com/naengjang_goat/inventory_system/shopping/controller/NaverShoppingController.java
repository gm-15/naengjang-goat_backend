package com.naengjang_goat.inventory_system.shopping.controller;

import com.naengjang_goat.inventory_system.shopping.dto.NaverShoppingItemDto;
import com.naengjang_goat.inventory_system.shopping.service.NaverShoppingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 네이버 쇼핑 검색 API 컨트롤러
 * 재료명으로 최저가 구매처 검색 → 링크 제공
 *
 * MockAuthFilter 적용 (X-User-Id 헤더 필요)
 */
@RestController
@RequestMapping("/shopping")
@RequiredArgsConstructor
public class NaverShoppingController {

    private final NaverShoppingService naverShoppingService;

    /**
     * 재료명으로 네이버 쇼핑 검색
     *
     * GET /shopping/search?query=돼지고기&display=5
     *
     * Response: [ { title, link, image, lprice, hprice, mallName, ... } ]
     */
    @GetMapping("/search")
    public ResponseEntity<List<NaverShoppingItemDto>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int display) {

        List<NaverShoppingItemDto> result = naverShoppingService.search(query, display);
        return ResponseEntity.ok(result);
    }

    /**
     * 재료명으로 최저가 Top 5만 반환 (메인 대시보드용)
     *
     * GET /shopping/lowest?query=돼지고기
     */
    @GetMapping("/lowest")
    public ResponseEntity<List<NaverShoppingItemDto>> lowest(@RequestParam String query) {
        List<NaverShoppingItemDto> result = naverShoppingService.searchByIngredientName(query);
        return ResponseEntity.ok(result);
    }
}
