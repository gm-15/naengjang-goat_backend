package com.naengjang_goat.inventory_system.pricing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * price_records 테이블 엔티티 — 외부 가격 수집 공용 저장소.
 *
 * 출처:
 *  - 식자재왕 크롤러 (sim, Python/Selenium): crawler/ewangmart/main.py
 *  - 네이버 쇼핑 API (park): NaverOnlinePriceProvider
 *  - V1: 쿠팡·11번가·마켓컬리 확장 (V1-03)
 *
 * 스키마 근거:
 *  - 기본 8컬럼: crawler/ewangmart/schema.sql (sim 작성)
 *  - 추가 5컬럼: src/main/resources/db/migration/V001__price_records_extension.sql
 *
 * 주의:
 *  - {@code unitPricePerKg} 는 GENERATED ALWAYS AS ... STORED 컬럼 → JPA 에서 read-only
 *  - JPA ddl-auto=update 는 GENERATED COLUMN/UNIQUE 인덱스를 못 만듦 → V001 SQL 수동 적용 필수
 */
@Entity
@Table(name = "price_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수집처. 컨벤션: "{플랫폼}_{카테고리}". 예: "식자재왕_채소/과일", "네이버_축산물" */
    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "product_name", length = 255, nullable = false)
    private String productName;

    /** 판매가 (원, 정수). 100 ≤ price ≤ 500,000 범위만 저장. */
    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "currency", length = 10)
    private String currency;

    /** 플랫폼 내부 할인 여부 (KAMIS dropRatePct 와는 별개 축 — BR2-10) */
    @Column(name = "is_discount", nullable = false)
    private boolean discount;

    @Column(name = "product_url", columnDefinition = "TEXT")
    private String productUrl;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    // ---------- V001 추가 컬럼 ----------

    /** Ingredient.id 매핑. 매칭 배치(IngredientMatcher) 가 채움. NULL 이면 응답 제외. */
    @Column(name = "ingredient_id")
    private Long ingredientId;

    /**
     * product_name 파싱 무게(g).
     * 액체(L/mL)는 LiquidDensity 적용 후 g 단위.
     * NULL → BR2-9 에 따라 응답 제외.
     */
    @Column(name = "weight_grams")
    private Integer weightGrams;

    /**
     * 원/kg 정규화. DB 의 GENERATED ALWAYS AS (price * 1000 / weight_grams) STORED.
     * JPA 에서 직접 INSERT/UPDATE 불가 — DB 가 자동 계산.
     */
    @Column(name = "unit_price_per_kg", insertable = false, updatable = false)
    private Long unitPricePerKg;

    /** 소스별 상품 ID. 식자재왕=gno, 네이버=productId. UNIQUE 키 일부. NOT NULL. */
    @Column(name = "raw_product_id", length = 100, nullable = false)
    private String rawProductId;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    // ---------- 팩토리 ----------

    /**
     * 외부 수집 시점 호출. ingredient_id 는 매칭 배치가 후속 채움.
     * unit_price_per_kg 는 DB 가 자동 계산.
     */
    public static PriceRecord ofExternal(
            String source,
            String productName,
            int price,
            boolean discount,
            String productUrl,
            String rawProductId,
            String imageUrl,
            Integer weightGrams) {
        PriceRecord r = new PriceRecord();
        r.source        = source;
        r.productName   = productName;
        r.price         = price;
        r.currency      = "KRW";
        r.discount      = discount;
        r.productUrl    = productUrl;
        r.rawProductId  = rawProductId;
        r.imageUrl      = imageUrl;
        r.weightGrams   = weightGrams;
        r.ingredientId  = null;
        r.fetchedAt     = LocalDateTime.now();
        return r;
    }

    /** 매칭 배치가 ingredient_id 채울 때 사용. */
    public void assignIngredient(Long ingredientId) {
        this.ingredientId = ingredientId;
    }
}
