package com.naengjang_goat.inventory_system.sale;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryRepository;
import com.naengjang_goat.inventory_system.inventory.repository.RawMaterialRepository;
import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeItemRepository;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeRepository;
import com.naengjang_goat.inventory_system.sale.dto.OrderRequestDto;
import com.naengjang_goat.inventory_system.sale.service.SaleService;
import com.naengjang_goat.inventory_system.user.User;
import com.naengjang_goat.inventory_system.user.UserRole;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class SaleServiceIntegrationTest {

    @Autowired private SaleService saleService;
    @Autowired private UserRepository userRepository;
    @Autowired private RawMaterialRepository rawMaterialRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RecipeItemRepository recipeItemRepository;
    @Autowired private RedissonClient redissonClient;

    private Long recipeId;
    private Long userId;

    @BeforeEach
    void setUp() {
        // 1) 사용자 생성
        User user = new User();
        user.setUsername("owner");
        user.setPassword("1234");
        user.setOwnerName("상명식당");
        user.setRole(UserRole.OWNER);
        userRepository.save(user);
        userId = user.getId();

        // 2) 재료 생성
        RawMaterial pasta = new RawMaterial();
        pasta.setName("파스타면");
        pasta.setUser(user);
        rawMaterialRepository.save(pasta);

        RawMaterial sauce = new RawMaterial();
        sauce.setName("토마토 소스");
        sauce.setUser(user);
        rawMaterialRepository.save(sauce);

        // 3) 초기 재고 2kg / 3L
        inventoryRepository.save(new Inventory(pasta, 2000.0));
        inventoryRepository.save(new Inventory(sauce, 3000.0));

        // 4) 레시피 생성 (토마토 파스타)
        Recipe recipe = new Recipe();
        recipe.setName("토마토 파스타");
        recipe.setPrice(9000);
        recipe.setUser(user);
        recipeRepository.save(recipe);
        recipeId = recipe.getId();

        // 5) 레시피 구성 (1인분: 면 100g, 소스 150ml)
        recipeItemRepository.save(new RecipeItem(recipe, pasta, 100.0, "g"));
        recipeItemRepository.save(new RecipeItem(recipe, sauce, 150.0, "ml"));
    }

    @Test
    void test_Redis_Lock_동시성_재고_차감() throws Exception {
        int threadCount = 10; // 동시 주문 10명
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        OrderRequestDto orderRequest = new OrderRequestDto(recipeId, 1);

        // 동시에 주문 요청
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    saleService.createSale(orderRequest, userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 종료까지 대기

        // ---- 결과 검증 ----
        Inventory pastaInv = inventoryRepository.findByRawMaterialId(
                rawMaterialRepository.findByName("파스타면").get().getId()
        ).get();

        Inventory sauceInv = inventoryRepository.findByRawMaterialId(
                rawMaterialRepository.findByName("토마토 소스").get().getId()
        ).get();

        // 판매량: 10명 → 면 100g씩 → 1000g 차감
        assertThat(pastaInv.getStockQuantity()).isEqualTo(1000.0);

        // 소스 150ml씩 → 1500ml 차감
        assertThat(sauceInv.getStockQuantity()).isEqualTo(1500.0);
    }
}
