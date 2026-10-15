package com.naengjang_goat.inventory_system.analysis.domain;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * KAMIS 시세 데이터 엔티티 (v2.1)
 * 구 PriceHistory 대체 — product_name 제거, Ingredient FK로 교체
 *
 * retail_price / wholesale_price는 요청한 유통 구분에 따라 분리한다.
 * KAMIS dpr1/dpr2/dpr4는 유통 구분이 아닌 서로 다른 관측일의 가격이다.
 * String 타입 유지: KAMIS가 "1,234" 형식으로 반환하기 때문
 */
@Entity
@Table(name = "market_price", uniqueConstraints = @UniqueConstraint(
        name = "uk_market_price_observation",
        columnNames = {"ingredient_id", "source", "price_basis", "reported_date", "observation_key"}))
@Getter
@Setter
@NoArgsConstructor
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "retail_price")
    private String retailPrice; // KAMIS 소매가 (예: "2,500")

    @Column(name = "wholesale_price")
    private String wholesalePrice; // KAMIS 도매가 (예: "1,800")

    @Column(name = "unit")
    private String unit; // 시세 기준 단위

    @Column(name = "reported_date", nullable = false)
    private LocalDate reportedDate; // 시세 공표일

    @Column(name = "source", nullable = false, length = 10)
    private String source = "KAMIS"; // 출처: KAMIS | EKAPE

    @Column(name = "price_basis", nullable = false, length = 16)
    private String priceBasis = "LEGACY";

    @Column(name = "observation_key", length = 64)
    private String observationKey;

    @Column(name = "kind_name", length = 100)
    private String kindName;

    @Column(name = "grade", length = 100)
    private String grade;

    public MarketPrice(Ingredient ingredient, String retailPrice, String wholesalePrice,
                       String unit, LocalDate reportedDate) {
        this.ingredient = ingredient;
        this.retailPrice = retailPrice;
        this.wholesalePrice = wholesalePrice;
        this.unit = unit;
        this.reportedDate = reportedDate;
        this.source = "KAMIS";
        this.priceBasis = wholesalePrice != null ? "WHOLESALE" : "RETAIL";
    }
}
