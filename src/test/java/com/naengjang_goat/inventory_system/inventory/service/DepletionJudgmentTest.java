package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.domain.StockGrade;
import com.naengjang_goat.inventory_system.inventory.dto.DepletionResult;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.menu.repository.RecipeBomRepository;
import com.naengjang_goat.inventory_system.order.domain.ChannelType;
import com.naengjang_goat.inventory_system.order.domain.Order;
import com.naengjang_goat.inventory_system.order.domain.OrderItem;
import com.naengjang_goat.inventory_system.order.domain.OrderStatus;
import com.naengjang_goat.inventory_system.order.repository.OrderRepository;
import com.naengjang_goat.inventory_system.settings.domain.DayOfWeekType;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발주 판단 기준 검증 — 판매량·재고·발주 요일을 바꿔가며 기대한 값이 나오는지 확인한다.
 *
 * 검증 대상 (2026-04-29 확정 규칙):
 *   일 평균 소모량 = 최근 30일 주문 수량 × BOM 소모량 ÷ 30 (취소 주문 제외)
 *   재고율        = 현재고 ÷ (다음 발주일까지 남은 일수 × 일 평균 소모량)
 *   등급          = 0.60 초과 충분 / 0.30 초과 보통 / 그 이하 위험
 *   소진 예정일    = 오늘 + ceil(현재고 ÷ 일 평균 소모량)
 *   발주 알림      = 등급이 위험이고, 소진 예정일이 다음 발주일보다 이른 경우
 *
 * 날짜 의존 제거: 발주 요일을 "오늘 + N일의 요일"로 저장해 실행일과 무관하게 거리 N 을 만든다.
 * 기대값은 공식을 다시 계산하지 않고 시나리오에 숫자로 못박는다.
 *
 * 주의: 로컬 DB 가 아닌 검증용 DB(DB_URL)로 실행할 것. 자기 데이터만 넣고 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DepletionJudgmentTest {

    /** 메뉴 1인분당 재료 소모량 (g). 판매 30인분 → 3,000g ÷ 30일 = 100g/일 */
    private static final BigDecimal BOM_PER_SERVING_G = new BigDecimal("100");

    @Autowired private DepletionCalculatorService depletionCalculatorService;
    @Autowired private LowStockService lowStockService;
    @Autowired private UserRepository userRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private RecipeBomRepository recipeBomRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private StoreSettingsRepository settingsRepository;

    private User user;
    private final List<Ingredient> ingredients = new ArrayList<>();
    private final List<Menu> menus = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (user == null) return;
        orderRepository.deleteAll(orderRepository.findAll().stream()
                .filter(o -> o.getUser().getId().equals(user.getId())).toList());
        menus.forEach(m -> recipeBomRepository.deleteAll(recipeBomRepository.findAllByMenuId(m.getId())));
        menuRepository.deleteAll(menus);
        ingredients.forEach(i -> batchRepository.deleteAll(
                batchRepository.findAllByIngredientIdAndQuantityGreaterThanOrderByExpirationDateAsc(
                        i.getId(), BigDecimal.valueOf(-1))));
        ingredientRepository.deleteAll(ingredients);
        settingsRepository.findByUserId(user.getId()).ifPresent(settingsRepository::delete);
        userRepository.delete(user);
        ingredients.clear();
        menus.clear();
        user = null;
    }

    /**
     * 시나리오.
     *
     * @param label            케이스 설명
     * @param servings          최근 30일 판매 인분 (완료)
     * @param canceledServings  같은 기간 취소된 판매 인분
     * @param bomQuantity       1인분 소모량
     * @param bomUnit           소모량 단위 (재료 기본 단위는 항상 g)
     * @param stockGrams        현재 재고 (g)
     * @param orderDayDistance  오늘부터 발주일까지 일수 (1~7)
     * @param expectedDailyAvg  기대 일 평균 소모량 (g)
     * @param expectedRatio     기대 재고율 (판매 없으면 Double.MAX_VALUE)
     * @param expectedGrade     기대 등급
     * @param expectedDepletionOffset 기대 소진 예정일 (오늘 + n일, 없으면 null)
     * @param expectedAlert     기대 발주 알림 여부
     */
    record Scenario(String label, int servings, int canceledServings,
                    String bomQuantity, String bomUnit,
                    String stockGrams, int orderDayDistance,
                    String expectedDailyAvg, double expectedRatio, StockGrade expectedGrade,
                    Integer expectedDepletionOffset, boolean expectedAlert) {
        @Override public String toString() { return label; }
    }

    static Stream<Scenario> scenarios() {
        return Stream.of(
                new Scenario("판매 30인분·재고 200g·발주 3일 → 충분",
                        30, 0, "100", "g", "200", 3,
                        "100.000", 0.6667, StockGrade.SUFFICIENT, 2, false),
                new Scenario("판매 30인분·재고 60g·발주 3일 → 위험 + 알림",
                        30, 0, "100", "g", "60", 3,
                        "100.000", 0.2000, StockGrade.DANGER, 1, true),
                new Scenario("재고율 0.60 경계 → 충분 아니라 보통",
                        30, 0, "100", "g", "60", 1,
                        "100.000", 0.6000, StockGrade.NORMAL, 1, false),
                new Scenario("재고율 0.30 경계 → 위험, 소진일 = 발주일이면 알림 없음",
                        30, 0, "100", "g", "30", 1,
                        "100.000", 0.3000, StockGrade.DANGER, 1, false),
                new Scenario("재고·판매 동일, 발주 3일 → 충분",
                        30, 0, "100", "g", "300", 3,
                        "100.000", 1.0000, StockGrade.SUFFICIENT, 3, false),
                new Scenario("재고·판매 동일, 발주 6일 → 보통 (발주 요일만 달라짐)",
                        30, 0, "100", "g", "300", 6,
                        "100.000", 0.5000, StockGrade.NORMAL, 3, false),
                new Scenario("판매 데이터 없음 → 정렬 최후순위, 소진일 없음",
                        0, 0, "100", "g", "100", 3,
                        "0", Double.MAX_VALUE, StockGrade.SUFFICIENT, null, false),
                new Scenario("취소된 판매는 일 평균에서 제외",
                        15, 15, "100", "g", "60", 3,
                        "50.000", 0.4000, StockGrade.NORMAL, 2, false),
                new Scenario("오늘이 발주 요일이면 다음 주 기준(7일)",
                        30, 0, "100", "g", "60", 7,
                        "100.000", 0.0857, StockGrade.DANGER, 1, true),
                new Scenario("BOM 단위 kg → 재료 단위 g 로 환산해 계산",
                        30, 0, "0.1", "kg", "60", 3,
                        "100.000", 0.2000, StockGrade.DANGER, 1, true)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("scenarios")
    void 판정_기준_검증(Scenario s) {
        user = userRepository.save(newUser());
        Ingredient ingredient = saveIngredient("검증 재료");
        saveStock(ingredient, s.stockGrams());
        Menu menu = saveMenuWithBom(ingredient, s.bomQuantity(), s.bomUnit());
        saveSettings(s.orderDayDistance());
        if (s.servings() > 0) saveOrder(menu, s.servings(), OrderStatus.COMPLETED);
        if (s.canceledServings() > 0) saveOrder(menu, s.canceledServings(), OrderStatus.CANCELED);

        DepletionResult result = depletionCalculatorService.calculate(user.getId(), ingredient.getId());

        assertThat(result.getDailyAvgSales()).isEqualByComparingTo(new BigDecimal(s.expectedDailyAvg()));
        assertThat(result.getNextOrderDayDistance()).isEqualTo(s.orderDayDistance());
        assertThat(result.getStockRatio()).isCloseTo(s.expectedRatio(), org.assertj.core.data.Offset.offset(0.0001));
        assertThat(result.getGrade()).isEqualTo(s.expectedGrade());
        assertThat(result.getEstimatedDepletionDate()).isEqualTo(
                s.expectedDepletionOffset() == null ? null : LocalDate.now().plusDays(s.expectedDepletionOffset()));
        assertThat(result.isOrderAlert()).isEqualTo(s.expectedAlert());
    }

    @Test
    @DisplayName("저재고 목록은 위험한 재료를 먼저 반환한다")
    void 저재고_정렬() {
        user = userRepository.save(newUser());
        Ingredient danger = saveIngredient("위험 재료");     // 재고율 0.20
        Ingredient enough = saveIngredient("충분 재료");     // 재고율 0.6667
        saveStock(danger, "60");
        saveStock(enough, "200");
        Menu menu = saveMenuWithBom(danger, "100", "g");
        addBom(menu, enough, "100", "g");
        saveSettings(3);
        saveOrder(menu, 30, OrderStatus.COMPLETED);

        List<LowStockItemDto> all = lowStockService.getTopLowStock(user.getId(), 10);
        List<LowStockItemDto> top1 = lowStockService.getTopLowStock(user.getId(), 1);

        assertThat(all).extracting(LowStockItemDto::getIngredientName)
                .containsExactly("위험 재료", "충분 재료");
        assertThat(all).extracting(LowStockItemDto::getGrade)
                .containsExactly(StockGrade.DANGER, StockGrade.SUFFICIENT);
        assertThat(top1).extracting(LowStockItemDto::getIngredientName).containsExactly("위험 재료");
    }

    // ─── setup helpers ──────────────────────────────────────────────────────

    private User newUser() {
        return new User("depletion-" + UUID.randomUUID(), "pw", "테스트", Role.OWNER);
    }

    private Ingredient saveIngredient(String name) {
        Ingredient saved = ingredientRepository.save(
                new Ingredient(user, name, "g", new BigDecimal("100")));
        ingredients.add(saved);
        return saved;
    }

    private void saveStock(Ingredient ingredient, String grams) {
        batchRepository.save(new InventoryBatch(ingredient, new BigDecimal(grams),
                BigDecimal.ONE, LocalDate.now(), LocalDate.now().plusMonths(6)));
    }

    private Menu saveMenuWithBom(Ingredient ingredient, String quantity, String unit) {
        Menu menu = menuRepository.save(new Menu(user, "검증 메뉴", 10000));
        menus.add(menu);
        addBom(menu, ingredient, quantity, unit);
        return menu;
    }

    private void addBom(Menu menu, Ingredient ingredient, String quantity, String unit) {
        recipeBomRepository.save(new RecipeBom(menu, ingredient, new BigDecimal(quantity), unit));
    }

    /** 발주 요일을 "오늘 + distance 일"의 요일로 저장 → calcNextOrderDayDistance 가 distance 를 돌려준다. */
    private void saveSettings(int distance) {
        DayOfWeek target = distance == 7
                ? LocalDate.now().getDayOfWeek()
                : LocalDate.now().plusDays(distance).getDayOfWeek();
        DayOfWeekType orderDay = DayOfWeekType.valueOf(target.name().substring(0, 3));
        settingsRepository.save(new StoreSettings(user, LocalTime.of(11, 0), LocalTime.of(22, 0),
                orderDay, DayOfWeekType.SUN));
    }

    private void saveOrder(Menu menu, int servings, OrderStatus status) {
        Order order = new Order(user, ChannelType.POS, menu.getPrice() * servings);
        order.setOrderStatus(status);
        order.addItem(new OrderItem(order, menu, servings));
        orderRepository.save(order);
    }
}
