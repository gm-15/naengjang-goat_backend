package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.dto.BatchRequest;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 재고 배치 단건 입력 서비스.
 *
 * Excel 일괄 업로드(InventoryExcelService)와 역할 분리.
 * 단건 JSON 요청 → InventoryBatch 생성 → 저장.
 */
@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;

    /**
     * 재고 배치 단건 등록.
     *
     * @param userId  JWT에서 추출한 점주 ID
     * @param request 입고 정보
     * @return 저장된 InventoryBatch
     * @throws ResponseStatusException 404 — 재료 없음 / 403 — 타인 재료
     */
    @Transactional
    public InventoryBatch create(Long userId, BatchRequest request) {
        // 1. 재료 조회 + 소유권 검증
        Ingredient ingredient = ingredientRepository.findById(request.ingredientId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "재료 없음: " + request.ingredientId()));

        if (!ingredient.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한 없음");
        }

        // 2. 유통기한 검증
        if (request.expirationDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "유통기한이 오늘보다 이전입니다: " + request.expirationDate());
        }

        // 3. 배치 생성
        LocalDate inboundDate = request.inboundDate() != null ? request.inboundDate() : LocalDate.now();
        InventoryBatch batch = new InventoryBatch(
                ingredient,
                request.quantity(),
                request.costPerUnit(),
                inboundDate,
                request.expirationDate()
        );

        return batchRepository.save(batch);
    }
}
