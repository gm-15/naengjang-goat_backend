package com.naengjang_goat.inventory_system.analysis.repository;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    @Modifying
    @Query(value = """
        INSERT INTO market_price
          (ingredient_id, retail_price, wholesale_price, unit, reported_date, source,
           price_basis, observation_key, kind_name, grade)
        VALUES (:ingredientId, :retail, :wholesale, 'kg', :date, 'KAMIS', :basis, :key, :kind, :grade)
        ON DUPLICATE KEY UPDATE
          retail_price = VALUES(retail_price), wholesale_price = VALUES(wholesale_price),
          unit = VALUES(unit), kind_name = VALUES(kind_name), grade = VALUES(grade)
        """, nativeQuery = true)
    int upsertKamisObservation(@Param("ingredientId") Long ingredientId,
                              @Param("retail") String retail,
                              @Param("wholesale") String wholesale,
                              @Param("date") LocalDate date,
                              @Param("basis") String basis,
                              @Param("key") String key,
                              @Param("kind") String kind,
                              @Param("grade") String grade);

    // 재료별 최근 N개 시세 (내림차순)
    List<MarketPrice> findTop30ByIngredientIdOrderByReportedDateDesc(Long ingredientId);

    // 재료별 기간 조회
    List<MarketPrice> findAllByIngredientIdAndReportedDateBetween(
            Long ingredientId, LocalDate start, LocalDate end);

    // 특정 날짜 전체 시세
    List<MarketPrice> findByReportedDate(LocalDate date);

    // 최신순 전체 조회
    List<MarketPrice> findAllByOrderByReportedDateDesc();

    // EKAPE 중복 삽입 방지 (ingredientId + 날짜 + 출처 조합 존재 여부)
    boolean existsByIngredientIdAndReportedDateAndSource(Long ingredientId, LocalDate reportedDate, String source);
}
