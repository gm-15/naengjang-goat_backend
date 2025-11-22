package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.domain.SaleHistory;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryRepository;
import com.naengjang_goat.inventory_system.inventory.repository.SaleHistoryRepository;
import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeItemRepository;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final InventoryRepository inventoryRepository;
    private final SaleHistoryRepository saleHistoryRepository;
    private final StringRedisTemplate redis;

    @Transactional
    public void processSale(Long userId, Long recipeId, int quantity) {

        String lockKey = "lock:recipe:" + recipeId;
        boolean isLocked = false;

        // -------------------------------------------------------
        // 🔥 수정됨: 스핀 락 (Spin Lock) 적용
        // 최대 10초(10,000ms) 동안 락 획득을 시도하며 대기함
        // -------------------------------------------------------
        long waitTime = 10000;  // 최대 대기 시간 (10초)
        long endTime = System.currentTimeMillis() + waitTime;

        while (System.currentTimeMillis() < endTime) {
            // 락 획득 시도 (락 유효 시간: 5초)
            Boolean result = redis.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);

            if (result != null && result) {
                isLocked = true;
                break; // 락 획득 성공 -> 루프 탈출
            }

            try {
                // 락 획득 실패 시 0.1초 대기 후 재시도 (CPU 과부하 방지)
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("락 대기 중 인터럽트 발생");
            }
        }

        // 10초가 지나도 락을 못 얻었으면 에러 발생
        if (!isLocked) {
            throw new IllegalStateException("주문이 몰려 잠금 획득 실패 (Timeout)");
        }

        try {
            // ============================
            // 🟢 비즈니스 로직 시작
            // ============================
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            Recipe recipe = recipeRepository.findById(recipeId)
                    .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

            List<RecipeItem> items = recipeItemRepository.findAllByRecipeId(recipeId);

            for (RecipeItem item : items) {

                double required = item.getQuantity() * quantity;

                Inventory inventory = inventoryRepository.findByRawMaterialId(item.getRawMaterial().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Inventory not found: " + item.getRawMaterial().getName()));

                double newQuantity = inventory.getStockQuantity() - required;

                if (newQuantity < 0) {
                    throw new IllegalArgumentException("Insufficient stock for " + item.getRawMaterial().getName());
                }

                inventory.setStockQuantity(newQuantity);
                inventoryRepository.save(inventory);
            }

            int totalAmount = recipe.getPrice() * quantity;

            SaleHistory history = new SaleHistory(user, recipeId, quantity, totalAmount);
            saleHistoryRepository.save(history);

        } finally {
            // ============================
            // 🔓 락 해제 (필수)
            // ============================
            redis.delete(lockKey);
        }
    }
}