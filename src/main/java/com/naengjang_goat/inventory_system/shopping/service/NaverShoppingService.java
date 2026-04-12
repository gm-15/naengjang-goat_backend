package com.naengjang_goat.inventory_system.shopping.service;

import com.naengjang_goat.inventory_system.shopping.client.NaverShoppingClient;
import com.naengjang_goat.inventory_system.shopping.dto.NaverShoppingItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NaverShoppingService {

    private static final int DEFAULT_DISPLAY = 5;

    private final NaverShoppingClient naverShoppingClient;

    /**
     * 재료명으로 네이버 쇼핑 검색 (최저가 Top 5)
     */
    public List<NaverShoppingItemDto> searchByIngredientName(String ingredientName) {
        return naverShoppingClient.search(ingredientName, DEFAULT_DISPLAY);
    }

    /**
     * 재료명 + 결과 수 지정 검색
     */
    public List<NaverShoppingItemDto> search(String query, int display) {
        return naverShoppingClient.search(query, display);
    }
}
