package com.naengjang_goat.inventory_system.pricing.provider;

import com.naengjang_goat.inventory_system.pricing.domain.PriceRecord;

import java.util.List;
import java.util.Optional;

/**
 * 외부 가격 소스 공통 추상화.
 *
 * 구현체:
 *  - NaverOnlinePriceProvider: 네이버 쇼핑 검색 API 호출 + price_records 저장
 *  - SikjajaewangPriceReader: 팀원 크롤러가 적재한 row 를 DB 에서 조회만 (수집은 Python 측)
 *  - V1: 쿠팡·11번가·마켓컬리 확장
 *
 * fetchLatest 정책:
 *  - 캐시 만료 시간 이내 row 가 있으면 그대로 반환 (외부 호출 안 함)
 *  - 만료/없음 → 외부 호출 후 저장 → 신규 row 반환 (구현체 자유)
 *  - SikjajaewangPriceReader 는 외부 호출 없이 DB 조회만
 */
public interface OnlinePriceProvider {

    /** 소스 식별자 prefix. price_records.source 가 이 값으로 시작. 예: "네이버", "식자재왕" */
    String sourcePrefix();

    /**
     * 해당 ingredient 의 최신 가격 row 를 반환.
     *
     * @param ingredientId ingredient.id
     * @param ingredientName ingredient.name (외부 검색 쿼리용)
     * @return 가장 신선한 PriceRecord (소스마다 N개 가능). 데이터 없으면 빈 리스트.
     */
    List<PriceRecord> fetchLatest(Long ingredientId, String ingredientName);

    /** 단일 최신 row 만 필요할 때 helper. */
    default Optional<PriceRecord> fetchSingleLatest(Long ingredientId, String ingredientName) {
        return fetchLatest(ingredientId, ingredientName).stream().findFirst();
    }
}
