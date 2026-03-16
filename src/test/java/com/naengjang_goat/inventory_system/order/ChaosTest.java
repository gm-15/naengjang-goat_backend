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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 카오스 엔지니어링 — Redis 장애 주입 테스트
 *
 * 테스트 목적:
 *   1. SPIN: Redis 장애 시 예외 전파 → 서비스 중단 (Fallback 없음)
 *   2. REDISSON: Redis 장애 시 Circuit Breaker OPEN → PESSIMISTIC Fallback → 서비스 무중단
 *
 * 왜 REDISSON + Circuit Breaker를 선택했는가를 수치로 증명.
 *
 * 전제: Docker 환경에서 'redis-test' 컨테이너가 실행 중이어야 함.
 *   docker run -d --name redis-test -p 6379:6379 redis:7-alpine
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChaosTest {

    @Autowired private OrderService           orderService;
    @Autowired private LockStrategyHolder     lockStrategyHolder;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired private OrderRepository      orderRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private MenuRepository       menuRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private UserRepository       userRepository;

    private Long userId;
    private Long menuId;
    private Long ingredientId;

    @BeforeEach
    void setUp() throws Exception {
        ensureRedisRunning();
        resetCircuitBreaker();

        // 데이터 정리 (FK 순서)
        orderRepository.deleteAll();
        batchRepository.deleteAll();
        menuRepository.deleteAll();
        ingredientRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 데이터 생성
        User user = userRepository.save(new User("chaos_owner", "pw", "카오스점주", Role.OWNER));
        userId = user.getId();

        Ingredient ingredient = ingredientRepository.save(
                new Ingredient(user, "양파", "g", new BigDecimal("10.000")));
        ingredientId = ingredient.getId();

        // 재고 500g (테스트 중 여러 번 호출해도 충분)
        batchRepository.save(new InventoryBatch(
                ingredient,
                new BigDecimal("500.000"),
                new BigDecimal("100.00"),
                LocalDate.now(),
                LocalDate.now().plusDays(7)));

        Menu menu = menuRepository.save(new Menu(user, "양파볶음", 5000));
        menuId = menu.getId();
        menu.getBom().add(new RecipeBom(menu, ingredient, new BigDecimal("1.000"), "g"));
        menuRepository.save(menu);
    }

    @AfterEach
    void tearDown() throws Exception {
        ensureRedisRunning(); // 테스트 후 Redis 복구
        resetCircuitBreaker();
        lockStrategyHolder.setCurrentType(LockType.REDISSON); // 기본값 복원
    }

    // -------------------------------------------------------------------------
    // Test 1: SPIN — Redis 장애 시 예외 전파, 서비스 중단
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("[SPIN] Redis 장애 → 예외 전파, 서비스 중단 재현")
    void spin_Redis장애시_예외전파() throws Exception {
        lockStrategyHolder.setCurrentType(LockType.SPIN);

        OrderRequest request = new OrderRequest(
                ChannelType.POS, List.of(new OrderItemRequest(menuId, 1)));

        // Redis 정상 상태에서 1건 성공 확인
        orderService.processOrder(userId, request);
        System.out.println("[SPIN] Redis 정상 상태: 주문 1건 성공");

        // Redis 강제 종료
        stopRedis();
        System.out.println("[SPIN] Redis 강제 종료 완료");

        // SPIN은 Fallback 없음 → 예외 전파 → 서비스 중단
        Exception ex = assertThrows(Exception.class,
                () -> orderService.processOrder(userId, request));
        System.out.println("[SPIN] Redis 장애 시 예외 발생 ✅: " + ex.getClass().getSimpleName());
        System.out.println("[SPIN] 결론: Redis 단일 장애점 → 서비스 전체 중단. Fallback 없음.");
    }

    // -------------------------------------------------------------------------
    // Test 2: REDISSON — Redis 장애 → Circuit Breaker OPEN → PESSIMISTIC Fallback
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("[REDISSON] Redis 장애 → Circuit Breaker OPEN → PESSIMISTIC Fallback → 서비스 무중단")
    void redisson_Redis장애시_CircuitBreaker_PESSIMISTIC_Fallback() throws Exception {
        lockStrategyHolder.setCurrentType(LockType.REDISSON);

        OrderRequest request = new OrderRequest(
                ChannelType.POS, List.of(new OrderItemRequest(menuId, 1)));

        // Redis 정상 상태 확인
        orderService.processOrder(userId, request);
        System.out.println("[REDISSON] Redis 정상 상태: 주문 1건 성공");

        // Redis 강제 종료
        stopRedis();
        System.out.println("[REDISSON] Redis 강제 종료 완료");

        // Circuit Breaker를 열기 위해 실패 호출 반복
        // application-test.yml: slidingWindowSize=3, minimumNumberOfCalls=3, failureRateThreshold=50%
        // → 3번 연속 실패 시 Circuit OPEN
        int failCount = 0;
        for (int i = 0; i < 3; i++) {
            try {
                orderService.processOrder(userId, request);
            } catch (Exception e) {
                failCount++;
                System.out.printf("[REDISSON] 실패 %d/3 (Redis 연결 불가): %s%n",
                        failCount, e.getClass().getSimpleName());
            }
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisLock");
        System.out.println("[REDISSON] Circuit Breaker 상태: " + cb.getState());
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Circuit OPEN 후 호출 → Redis 접근 없이 바로 PESSIMISTIC Fallback
        // PessimisticLockStrategy가 SELECT FOR UPDATE로 재고 차감 → 성공해야 함
        System.out.println("[REDISSON] Circuit OPEN 후 주문 시도 → PESSIMISTIC Fallback...");
        orderService.processOrder(userId, request);
        System.out.println("[REDISSON] PESSIMISTIC Fallback 성공 ✅ — 서비스 무중단 확인");
        System.out.println("[REDISSON] 결론: Redis 장애에도 Circuit Breaker가 자동 전환. DB Lock으로 무중단 운영.");
    }

    // -------------------------------------------------------------------------
    // 헬퍼 메서드
    // -------------------------------------------------------------------------

    private void stopRedis() throws Exception {
        Process p = new ProcessBuilder("docker", "stop", "redis-test")
                .redirectErrorStream(true)
                .start();
        p.waitFor(10, TimeUnit.SECONDS);
        Thread.sleep(500); // 컨테이너 완전 종료 대기
    }

    private void ensureRedisRunning() throws Exception {
        // 먼저 start 시도 (이미 실행 중이면 에러 무시)
        Process p = new ProcessBuilder("docker", "start", "redis-test")
                .redirectErrorStream(true)
                .start();
        p.waitFor(10, TimeUnit.SECONDS);
        Thread.sleep(1000); // Redis 준비 대기
    }

    private void resetCircuitBreaker() {
        try {
            circuitBreakerRegistry.circuitBreaker("redisLock").reset();
        } catch (Exception ignored) { }
    }
}
