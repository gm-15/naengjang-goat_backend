package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;

    /**
     * 특정 유저의 모든 재고 조회
     */
    public List<Inventory> getInventoriesByUser(Long userId) {
        return inventoryRepository.findAll().stream()
                .filter(inv -> inv.getRawMaterial().getUser().getId().equals(userId))
                .toList();
    }

    /**
     * 재고 직접 수정 (관리자/점주의 수동 입력)
     * - 락 필요 없음
     */
    public Inventory updateInventory(Long rawMaterialId, double newQuantity) {
        Inventory inventory = inventoryRepository.findByRawMaterialId(rawMaterialId)
                .orElseThrow(() -> new IllegalArgumentException("재고가 존재하지 않습니다."));
        inventory.setStockQuantity(newQuantity);
        return inventoryRepository.save(inventory);
    }

    /**
     * 주문 발생 시 재고 차감 (분산 락 적용)
     */
    @Transactional
    public void decreaseStock(Long rawMaterialId, double quantity) {

        String lockKey = "lock:inventory:" + rawMaterialId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도 (대기 5초, 점유 10초)
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {

                // 재고 조회
                Inventory inventory = inventoryRepository.findByRawMaterialId(rawMaterialId)
                        .orElseThrow(() -> new IllegalArgumentException("재고가 존재하지 않습니다."));

                // 재고 부족 체크
                if (inventory.getStockQuantity() < quantity) {
                    throw new IllegalStateException("재고가 부족합니다.");
                }

                // 재고 차감
                inventory.setStockQuantity(inventory.getStockQuantity() - quantity);
                inventoryRepository.save(inventory);

            } else {
                throw new IllegalStateException("다른 요청이 재고를 처리 중입니다. 잠시 후 다시 시도하세요.");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 대기 중 인터럽트 발생", e);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
