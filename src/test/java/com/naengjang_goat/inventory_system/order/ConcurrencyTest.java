package com.naengjang_goat.inventory_system.order;

import com.naengjang_goat.inventory_system.global.lock.LockStrategyHolder;
import com.naengjang_goat.inventory_system.global.lock.LockType;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.order.domain.ChannelType;
import com.naengjang_goat.inventory_system.order.dto.OrderItemRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderRequest;
import com.naengjang_goat.inventory_system.order.repository.OrderRepository;
import com.naengjang_goat.inventory_system.order.service.OrderService;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 테스트 — LockStrategy 별 재고 정합성 검증
 *
 * [Phase 1] NONE   → 재고 음수 발생 확인 (붕괴 재현)
 * [Phase 2] SPIN / REDISSON / PESSIMISTIC → 정합성 보장 확인
 *
 * 시나리오:
 *   - 재고 100g 짜리 배치 1개
 *   - 메뉴 1인분에 재료 1g 소요
 *   - 500 스레드 동시 주문 → 최종 재고 = 100 - 성공 횟수 * 1
 *
 * 주의: REDISSON / SPIN 전략은 Redis가 실행 중이어야 한다.
 *       로컬 Redis 없이 실행 시 NONE / PESSIMISTIC만 유효.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    private static final int THREAD_COUNT   = 500;
    private static final BigDecimal INITIAL_QTY = BigDecimal.valueOf(100);

    @Autowired private OrderService           orderService;
    @Autowired private LockStrategyHolder     lockStrategyHolder;
    @Autowired private UserRepository         userRepository;
    @Autowired private IngredientRepository   ingredientRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private MenuRepository         menuRepository;

    private Long userId;
    private Long menuId;
    private Long ingredientId;

    @Autowired private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        // FK 순서 맞춰 정리: orders → batches → menus(cascade bom) → ingredients → users
        orderRepository.deleteAll();
        batchRepository.deleteAll();
        menuRepository.deleteAll();
        ingredientRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 점주 생성
        User user = userRepository.save(
                new User("test_owner", "pw", "테스트점주", Role.OWNER));
        userId = user.getId();

        // 테스트용 재료 생성 (base 단위: g)
        Ingredient ingredient = ingredientRepository.save(
                new Ingredient(user, "테스트재료", "g", BigDecimal.valueOf(10)));
        ingredientId = ingredient.getId();

        // 배치: 100g, 유통기한 내일
        batchRepository.save(new InventoryBatch(
                ingredient, INITIAL_QTY, BigDecimal.ONE,
                LocalDate.now(), LocalDate.now().plusDays(7)));

        // 메뉴 + BOM: 1인분에 재료 1g
        Menu menu = menuRepository.save(new Menu(user, "테스트메뉴", 5000));
        RecipeBom bom = new RecipeBom(menu, ingredient, BigDecimal.ONE, "g");
        menu.addBom(bom);
        menuRepository.save(menu);
        menuId = menu.getId();
    }

    @ParameterizedTest(name = "[{index}] LockType={0}")
    @EnumSource(LockType.class)
    @DisplayName("500 스레드 동시 주문 — 전략별 재고 정합성")
    void concurrencyTest(LockType lockType) throws InterruptedException {
        lockStrategyHolder.setCurrentType(lockType);

        CountDownLatch ready  = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch done   = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        OrderRequest request = new OrderRequest(
                ChannelType.POS,
                List.of(new OrderItemRequest(menuId, 1))
        );

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 모든 스레드 준비 완료 후 동시 출발
                    orderService.processOrder(userId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();  // 500 스레드 모두 대기 상태
        start.countDown(); // 동시 출발
        done.await();   // 전부 완료 대기
        executor.shutdown();

        // 최종 재고 합산
        BigDecimal remaining = batchRepository.sumQuantityByIngredientId(ingredientId);

        System.out.printf(
                "%n[%s] 성공=%d, 실패=%d, 남은재고=%.3f%n",
                lockType, successCount.get(), failCount.get(), remaining
        );

        if (lockType == LockType.NONE) {
            // NONE은 붕괴 재현 — 음수 또는 잘못된 값이 나와야 테스트 의미 있음
            // 단순 출력만 하고 assert 없음 (붕괴 시나리오 로그 확인용)
            System.out.println("[NONE] 동시성 붕괴 시나리오 — 재고 정합성 미보장 (의도된 결과)");
        } else {
            // 락이 있으면: 성공 수 = 초기재고(100) 이하, 남은 재고 >= 0
            assertThat(successCount.get()).isLessThanOrEqualTo(INITIAL_QTY.intValue());
            assertThat(remaining).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            // 성공 수 + 남은 재고 = 초기 재고 (정합성)
            BigDecimal expected = INITIAL_QTY.subtract(BigDecimal.valueOf(successCount.get()));
            assertThat(remaining).isEqualByComparingTo(expected);
        }
    }
}
