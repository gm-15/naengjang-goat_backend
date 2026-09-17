package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.settings.domain.DayOfWeekType;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 저재고 조회 중 일부 계산이 실패해도 조회 전체가 롤백되지 않는지 검증.
 *
 * 버그: getTopLowStock 트랜잭션 안에서 @Transactional 메서드가 예외를 던지면, catch 로 삼켜도
 * 트랜잭션이 rollback-only 로 표시돼 커밋 시점에 UnexpectedRollbackException 이 난다.
 *
 * 주의: 자기 데이터만 넣고 지운다. 그래도 로컬 DB 가 아닌 검증용 DB(DB_URL)로 실행할 것.
 */
@SpringBootTest
@ActiveProfiles("test")
class LowStockServiceTransactionTest {

    @Autowired private LowStockService lowStockService;
    @Autowired private UserRepository userRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private StoreSettingsRepository settingsRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private DailySalesService dailySalesService;

    private User user;
    private Ingredient normal;
    private Ingredient failing;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("lowstock-tx-" + UUID.randomUUID(), "pw", "테스트", Role.OWNER));
        normal = ingredientRepository.save(new Ingredient(user, "정상 재료", "g", new BigDecimal("100")));
        failing = ingredientRepository.save(new Ingredient(user, "계산 실패 재료", "g", new BigDecimal("100")));
        given(dailySalesService.getDailyAvgSales(anyLong(), anyLong())).willReturn(BigDecimal.ZERO);
    }

    @AfterEach
    void tearDown() {
        settingsRepository.findByUserId(user.getId()).ifPresent(settingsRepository::delete);
        ingredientRepository.deleteAll(List.of(normal, failing));
        userRepository.delete(user);
    }

    @Test
    @DisplayName("영업 설정이 없는 점주도 저재고 목록을 받는다 (발주일 거리 기본 7일)")
    void settingsMissing() {
        List<LowStockItemDto> result = lowStockService.getTopLowStock(user.getId(), 10);

        assertThat(result).hasSize(2)
                .allSatisfy(item -> assertThat(item.getNextOrderDayDistance()).isEqualTo(7));
    }

    @Test
    @DisplayName("재료 하나의 소진 계산이 실패해도 나머지 재료는 반환된다")
    void oneIngredientFails() {
        saveSettings();
        failDailySalesOf(failing);

        List<LowStockItemDto> result = lowStockService.getTopLowStock(user.getId(), 10);

        assertThat(result).extracting(LowStockItemDto::getIngredientId).containsExactly(normal.getId());
    }

    @Test
    @DisplayName("알림 경로처럼 바깥 트랜잭션 안에서 호출해도 나머지 재료는 반환된다")
    void oneIngredientFailsInsideOuterTransaction() {
        saveSettings();
        failDailySalesOf(failing);
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);

        List<LowStockItemDto> result = readOnlyTx.execute(status -> lowStockService.getTopLowStock(user.getId(), 10));

        assertThat(result).extracting(LowStockItemDto::getIngredientId).containsExactly(normal.getId());
    }

    private void saveSettings() {
        settingsRepository.save(new StoreSettings(user, LocalTime.of(11, 0), LocalTime.of(22, 0),
                DayOfWeekType.MON, DayOfWeekType.SUN));
    }

    private void failDailySalesOf(Ingredient ingredient) {
        given(dailySalesService.getDailyAvgSales(user.getId(), ingredient.getId()))
                .willThrow(new IllegalStateException("테스트용 계산 실패"));
    }
}
