package com.naengjang_goat.inventory_system;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryRepository;
import com.naengjang_goat.inventory_system.inventory.repository.RawMaterialRepository;
import com.naengjang_goat.inventory_system.inventory.service.SaleService;
import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import com.naengjang_goat.inventory_system.recipe.domain.UnitType;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeItemRepository;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeRepository;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource; // ✅ 추가됨
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
// 👇 이 줄이 핵심입니다! 테스트 실행 시 DB 스키마를 싹 지우고 새로 만듭니다. (좀비 컬럼 해결)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create")
class SaleServiceIntegrationTest {

    @Autowired private SaleService saleService;
    @Autowired private UserRepository userRepository;
    @Autowired private RawMaterialRepository rawMaterialRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RecipeItemRepository recipeItemRepository;
    @Autowired private EntityManager em;

    @Autowired private TransactionTemplate transactionTemplate;

    private Long userId;
    private Long recipeId;

    @BeforeEach
    void cleanAndSetup() {
        transactionTemplate.execute(status -> {
            // ddl-auto=create 덕분에 테이블이 새로 생성되지만,
            // 테스트 반복 실행 시 데이터 누적을 방지하기 위해 Truncate는 유지하는 것이 안전합니다.
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE sale_history").executeUpdate(); // 이름 주의
            em.createNativeQuery("TRUNCATE TABLE recipe_item").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE recipe").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE inventory").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE raw_material").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE users").executeUpdate();
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

            em.flush();
            em.clear();

            User user = new User();
            user.setUsername("owner1");
            user.setPassword("pw1234");
            user.setOwnerName("상명식당");
            user.setRole(Role.OWNER);
            user.setActive(true);
            user = userRepository.save(user);
            this.userId = user.getId();

            RawMaterial tomato = new RawMaterial();
            tomato.setName("토마토");
            tomato.setUnitType(UnitType.WEIGHT);
            tomato.setUser(user);
            tomato = rawMaterialRepository.save(tomato);

            RawMaterial sauce = new RawMaterial();
            sauce.setName("소스");
            sauce.setUnitType(UnitType.WEIGHT);
            sauce.setUser(user);
            sauce = rawMaterialRepository.save(sauce);

            Inventory tomatoInv = new Inventory(tomato, 2000.0);
            tomatoInv.setStockUnit("g");
            inventoryRepository.save(tomatoInv);

            Inventory sauceInv = new Inventory(sauce, 3000.0);
            sauceInv.setStockUnit("g");
            inventoryRepository.save(sauceInv);

            Recipe recipe = new Recipe();
            recipe.setName("토마토 파스타");
            recipe.setPrice(9000);
            recipe.setUser(user);
            recipe = recipeRepository.save(recipe);
            this.recipeId = recipe.getId();

            RecipeItem item1 = new RecipeItem();
            item1.setRecipe(recipe);
            item1.setRawMaterial(tomato);
            item1.setQuantity(100.0);
            item1.setUnit("g");
            recipeItemRepository.save(item1);

            RecipeItem item2 = new RecipeItem();
            item2.setRecipe(recipe);
            item2.setRawMaterial(sauce);
            item2.setQuantity(150.0);
            item2.setUnit("g");
            recipeItemRepository.save(item2);

            em.flush();
            em.clear();
            return null;
        });
    }

    @Test
    @DisplayName("10명이 동시에 주문해도 재고 정확히 감소한다")
    void concurrent_sales() throws InterruptedException {

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    saleService.processSale(userId, recipeId, 1);
                } catch (Exception e) {
                    System.out.println("Concurrent Exception: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // ---------------------
        // 검증 (Verification)
        // ---------------------
        transactionTemplate.execute(status -> {
            Inventory tomatoInv = inventoryRepository.findAll().stream()
                    .filter(inv -> inv.getRawMaterial().getName().equals("토마토"))
                    .findFirst().orElseThrow();

            Inventory sauceInv = inventoryRepository.findAll().stream()
                    .filter(inv -> inv.getRawMaterial().getName().equals("소스"))
                    .findFirst().orElseThrow();

            // 초기 2000 - (100 * 10) = 1000
            assertThat(tomatoInv.getStockQuantity()).isEqualTo(1000.0);

            // 초기 3000 - (150 * 10) = 1500
            assertThat(sauceInv.getStockQuantity()).isEqualTo(1500.0);

            return null;
        });
    }
}