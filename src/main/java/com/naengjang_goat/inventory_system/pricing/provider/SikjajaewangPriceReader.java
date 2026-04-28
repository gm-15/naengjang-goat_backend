package com.naengjang_goat.inventory_system.pricing.provider;

import com.naengjang_goat.inventory_system.pricing.domain.PriceRecord;
import com.naengjang_goat.inventory_system.pricing.repository.PriceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 식자재왕 가격 조회 Provider — DB read-only.
 *
 * 수집은 별도 프로세스(crawler/ewangmart/main.py, sim 담당)가 담당.
 * 본 클래스는 DB 에 적재된 row 를 읽기만 한다.
 *
 * findLatestByIngredientPerSource 가 모든 source(식자재왕_*) 의 최신 row 를 한꺼번에 가져오므로
 * 본 Provider 는 OnlinePriceAggregator 입장에서 호출되지 않을 수도 있음 → 명시적 분리는
 * V1 에서 외부 가격 소스 추가 시 추상화 통일을 위함.
 */
@Service
@RequiredArgsConstructor
public class SikjajaewangPriceReader implements OnlinePriceProvider {

    private static final String SOURCE_PREFIX = "식자재왕";

    private final PriceRecordRepository priceRecordRepository;

    @Override
    public String sourcePrefix() {
        return SOURCE_PREFIX;
    }

    /**
     * DB 에 적재된 식자재왕 row 들 중 해당 ingredient 의 최신 1건 (소스별).
     * Aggregator 가 직접 priceRecordRepository.findLatestByIngredientPerSource 를 쓰는 경우
     * 본 메서드는 호출되지 않음.
     */
    @Override
    public List<PriceRecord> fetchLatest(Long ingredientId, String ingredientName) {
        return priceRecordRepository.findLatestByIngredientPerSource(ingredientId).stream()
                .filter(r -> r.getSource() != null && r.getSource().startsWith(SOURCE_PREFIX))
                .toList();
    }
}
