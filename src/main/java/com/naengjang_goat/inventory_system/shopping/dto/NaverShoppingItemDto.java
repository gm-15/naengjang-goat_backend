package com.naengjang_goat.inventory_system.shopping.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 네이버 쇼핑 검색 API 응답 아이템 DTO
 * GET https://openapi.naver.com/v1/search/shop.json
 */
@Getter
@Setter
@NoArgsConstructor
public class NaverShoppingItemDto {

    private String title;       // 상품명 (HTML 태그 포함 가능)
    private String link;        // 상품 URL
    private String image;       // 상품 이미지 URL
    private String lprice;      // 최저가
    private String hprice;      // 최고가
    private String mallName;    // 판매처 이름
    private String brand;       // 브랜드
    private String maker;       // 제조사
    private String category1;   // 카테고리 대분류
    private String category2;   // 카테고리 중분류
}
