package com.naengjang_goat.inventory_system.global.config;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseOrder;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import com.naengjang_goat.inventory_system.purchase.repository.PurchaseOrderRepository;
import com.naengjang_goat.inventory_system.settings.domain.DayOfWeekType;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import com.naengjang_goat.inventory_system.supplier.domain.Supplier;
import com.naengjang_goat.inventory_system.supplier.repository.SupplierRepository;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 앱 기동 시 데모 시드 데이터 생성.
 *
 * demo 유저가 없을 때만 1회 실행 (멱등).
 *
 * 생성 내용:
 *   - 데모 계정: username=demo / password=demo1234 / ownerName=데모점주
 *   - StoreSettings 1건 (sim, 2026-06-01) — 영업 11:00~22:00, 발주 월요일
 *   - KAMIS item_name 과 1:1 매칭되는 ingredient 19종
 *     (KamisPriceProcessor.findByName() 매칭 대상)
 *   - InventoryBatch 시드 — 각 ingredient 당 1 batch (sim, 2026-06-01)
 *     · 시안 발주 페이지 TOP 5 표현용 (재고율 = currentStock / warningThreshold)
 *   - PurchaseOrder 3건 (sim, 2026-06-01) — 시안 "마지막 발주" 표시용
 *     · 닭고기 2026-04-10 / 튀김가루 2026-04-12 / 양파 2026-04-15
 *
 * ※ 프로덕션에서는 이 클래스를 비활성화하거나 @Profile("demo")로 제한 권장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo1234";

    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final PasswordEncoder passwordEncoder;

    // 카테고리별 placeholder 이미지 (Unsplash) — sim, 2026-06-01.
    // 시안 /lowest-price/{id} 상단 이미지용. 향후 자체 CDN 으로 교체.
    private static final String IMG_VEG  = "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=800&q=80";
    private static final String IMG_MEAT = "https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?w=800&q=80";
    private static final String IMG_FISH = "https://images.unsplash.com/photo-1535400255456-9b58f78a4ba1?w=800&q=80";
    private static final String IMG_FRUIT= "https://images.unsplash.com/photo-1610832958506-aa56368176cf?w=800&q=80";
    private static final String IMG_GRAIN= "https://images.unsplash.com/photo-1568347355280-d33fdf77d42a?w=800&q=80";
    private static final String IMG_PROC = "https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=800&q=80";

    // KAMIS dailyPriceByCategoryList item_name → (baseUnit, kamisCategory, warningThreshold, uiCategory, imageUrl)
    // 이름은 KAMIS API 응답 item_name 과 정확히 일치해야 매칭됨.
    // uiCategory / imageUrl — sim, 2026-06-01 추가. 시안 /lowest-price 카테고리 칩 + 상세 이미지용.
    //
    // ※ warningThreshold = 권장 재고량 역할 (LowStockService.v1: stockRatio = currentStock / warningThreshold).
    //   양파(40000)·닭고기(50000)·튀김가루(30000) 은 시안 발주 페이지 카드 정확 매칭용 — sim, 2026-06-01.
    private static final List<IngredientSeed> SEEDS = List.of(
            // 채소류 (cat=200)
            new IngredientSeed("배추",     "g",  "VEGETABLES", new BigDecimal("3000"),  "채소", IMG_VEG),
            new IngredientSeed("양파",     "g",  "VEGETABLES", new BigDecimal("40000"), "채소", IMG_VEG),  // sim: 시안 권장 40kg
            new IngredientSeed("마늘",     "g",  "VEGETABLES", new BigDecimal("500"),   "채소", IMG_VEG),
            new IngredientSeed("대파",     "g",  "VEGETABLES", new BigDecimal("500"),   "채소", IMG_VEG),
            new IngredientSeed("무",       "g",  "VEGETABLES", new BigDecimal("2000"),  "채소", IMG_VEG),
            new IngredientSeed("건고추",   "g",  "VEGETABLES", new BigDecimal("300"),   "채소", IMG_VEG),
            // 수산물 (cat=600)
            new IngredientSeed("고등어",   "g",  "SEAFOOD",    new BigDecimal("500"),   "기타", IMG_FISH),
            new IngredientSeed("명태",     "g",  "SEAFOOD",    new BigDecimal("500"),   "기타", IMG_FISH),
            new IngredientSeed("오징어",   "g",  "SEAFOOD",    new BigDecimal("300"),   "기타", IMG_FISH),
            // 과일류 (cat=400)
            new IngredientSeed("사과",     "개", "FRUITS",     new BigDecimal("10"),    "기타", IMG_FRUIT),
            new IngredientSeed("배",       "개", "FRUITS",     new BigDecimal("5"),     "기타", IMG_FRUIT),
            // 곡물류 (cat=100)
            new IngredientSeed("쌀",       "g",  "GRAINS",     new BigDecimal("5000"),  "기타", IMG_GRAIN),
            // 축산물 (EKAPE) — EkapeApiClient 키워드 매핑 대상
            new IngredientSeed("삼겹살",   "g",  "LIVESTOCK",  new BigDecimal("500"),   "육류", IMG_MEAT),
            new IngredientSeed("목심",     "g",  "LIVESTOCK",  new BigDecimal("300"),   "육류", IMG_MEAT),
            new IngredientSeed("닭고기",   "g",  "LIVESTOCK",  new BigDecimal("50000"), "육류", IMG_MEAT),  // sim: 시안 권장 50kg
            new IngredientSeed("등심",     "g",  "LIVESTOCK",  new BigDecimal("300"),   "육류", IMG_MEAT),
            // sim 추가 (2026-06-01) — 시안 /lowest-price 노출 항목 + 소스/양념 카테고리 활성화
            new IngredientSeed("튀김가루", "g",  "PROCESSED",  new BigDecimal("30000"), "기타",      IMG_PROC),  // sim: 시안 권장 30kg
            new IngredientSeed("식용유",   "ml", "PROCESSED",  new BigDecimal("2000"),  "기타",      IMG_PROC),
            new IngredientSeed("진간장",   "ml", "PROCESSED",  new BigDecimal("1000"),  "소스/양념", IMG_PROC)
    );

    // 시안 발주 페이지 TOP 5 정확 매칭용 명시 재고. 미지정 ingredient 는 warningThreshold × 1.5 (충분) 자동 적용.
    // sim, 2026-06-01.
    private static final Map<String, BigDecimal> SEED_STOCK = Map.of(
            "닭고기",   new BigDecimal("5000"),    // ratio 10% — 시안 카드 1번
            "튀김가루", new BigDecimal("8000"),    // ratio 27% — 시안 카드 2번
            "양파",     new BigDecimal("12000"),   // ratio 30% — 시안 카드 3번
            "마늘",     new BigDecimal("200"),     // ratio 40% — TOP 5 카드 4번
            "식용유",   new BigDecimal("1000")     // ratio 50% — TOP 5 카드 5번
    );

    private static final BigDecimal SUFFICIENT_FACTOR = new BigDecimal("1.5");

    // 시안 발주 페이지 "마지막 발주" 일자 시드. sim, 2026-06-01.
    private static final Map<String, LocalDate> SEED_LAST_ORDER = Map.of(
            "닭고기",   LocalDate.of(2026, 4, 10),  // 시안 카드 1번
            "튀김가루", LocalDate.of(2026, 4, 12),  // 시안 카드 2번
            "양파",     LocalDate.of(2026, 4, 15)   // 시안 카드 3번 추정
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1. 데모 유저 조회 or 생성
        User demo = userRepository.findByUsername(DEMO_USERNAME).orElseGet(() -> {
            User c = userRepository.save(new User(
                    DEMO_USERNAME,
                    passwordEncoder.encode(DEMO_PASSWORD),
                    "데모점주",
                    Role.OWNER
            ));
            log.info("[DataInitializer] 데모 유저 생성 → id={}", c.getId());
            return c;
        });

        // 2. StoreSettings 시드 (sim, 2026-06-01)
        seedStoreSettingsIfMissing(demo);

        // 2-bis. Supplier 시드 (sim, 2026-06-04) — 3개 거래처 데모
        seedSuppliersIfMissing(demo);

        // 3. KAMIS/EKAPE 매칭용 ingredient 시드 — 미존재 항목만 생성 (멱등)
        // sim, 2026-06-01 — 기존 row 에 category/image_url 점진 보충도 처리 + InventoryBatch 시드
        int created = 0;
        int backfilled = 0;
        for (IngredientSeed seed : SEEDS) {
            var existingOpt = ingredientRepository
                    .findByUserIdAndName(demo.getId(), seed.name());
            Ingredient ingredient;
            if (existingOpt.isEmpty()) {
                ingredient = new Ingredient(
                        demo, seed.name(), seed.baseUnit(), seed.warningThreshold());
                ingredient.setKamisCategory(seed.kamisCategory());
                ingredient.setCategory(seed.uiCategory());     // sim, 2026-06-01
                ingredient.setImageUrl(seed.imageUrl());       // sim, 2026-06-01
                ingredientRepository.save(ingredient);
                created++;
            } else {
                // 기존 row — 새로 추가된 필드만 보충 (null 인 경우, 사용자 수정 보존)
                ingredient = existingOpt.get();
                boolean dirty = false;
                if (ingredient.getCategory() == null) {
                    ingredient.setCategory(seed.uiCategory());
                    dirty = true;
                }
                if (ingredient.getImageUrl() == null) {
                    ingredient.setImageUrl(seed.imageUrl());
                    dirty = true;
                }
                if (dirty) {
                    ingredientRepository.save(ingredient);
                    backfilled++;
                }
            }

            // InventoryBatch 시드 — 재고 없는 ingredient 에만 추가 (멱등)
            // sim, 2026-06-01 — 시안 발주 페이지 (재고율 TOP 5) 동작용
            seedInventoryBatchIfMissing(ingredient, seed);
        }

        if (created > 0 || backfilled > 0) {
            log.info("[DataInitializer] ingredient 시드 생성={}, 점진보충={}", created, backfilled);
        } else {
            log.info("[DataInitializer] ingredient 시드 모두 이미 존재 — 스킵");
        }

        // 4. PurchaseOrder 시드 (sim, 2026-06-01) — 시안 "마지막 발주" 표시용
        seedPurchaseOrdersIfMissing(demo);

        log.info("[DataInitializer] 로그인 정보 → username={} / password={}", DEMO_USERNAME, DEMO_PASSWORD);
    }

    /**
     * Supplier 3건 시드 — 데모 점주 거래처.
     * 이미 있으면 스킵. sim, 2026-06-04.
     */
    private void seedSuppliersIfMissing(User demo) {
        if (supplierRepository.existsByUserId(demo.getId())) {
            return;
        }
        supplierRepository.save(new Supplier(
                demo, "농협유통", "02-1234-5678",
                "서울특별시 강남구 테헤란로 123",
                "오전 11시 전 주문 → 당일 배송"
        ));
        supplierRepository.save(new Supplier(
                demo, "식자재마트 김씨", "010-9876-5432",
                "경기도 용인시 처인구 마평로 45",
                "주 2회(월/목) 발주, 현금 결제 시 3% 할인"
        ));
        supplierRepository.save(new Supplier(
                demo, "직거래 농장", "010-5555-6666",
                "충북 음성군 음성읍 농촌길 100",
                "도매가 협상 가능. 최소 발주량 10kg"
        ));
        log.info("[DataInitializer] Supplier 시드 3건 생성");
    }

    /**
     * StoreSettings 1건 시드 — 영업시간 + 발주/실사 요일.
     * 이미 있으면 스킵. sim, 2026-06-01.
     */
    private void seedStoreSettingsIfMissing(User demo) {
        if (storeSettingsRepository.findByUserId(demo.getId()).isPresent()) {
            return;
        }
        StoreSettings settings = new StoreSettings(
                demo,
                LocalTime.of(11, 0),   // 영업 시작
                LocalTime.of(22, 0),   // 영업 종료
                DayOfWeekType.MON,     // 발주 요일 — 매주 월요일
                DayOfWeekType.SUN      // 재고 실사 — 매주 일요일
        );
        storeSettingsRepository.save(settings);
        log.info("[DataInitializer] StoreSettings 시드 생성 (open=11:00 / close=22:00 / order=MON)");
    }

    /**
     * 해당 ingredient 가 보유한 InventoryBatch 합산이 0 이면 시드 batch 1개 추가.
     * sim, 2026-06-01.
     */
    private void seedInventoryBatchIfMissing(Ingredient ingredient, IngredientSeed seed) {
        BigDecimal currentStock = inventoryBatchRepository
                .sumQuantityByIngredientId(ingredient.getId());
        if (currentStock != null && currentStock.compareTo(BigDecimal.ZERO) > 0) {
            return;  // 이미 batch 있음 — 스킵
        }

        BigDecimal quantity = SEED_STOCK.getOrDefault(
                seed.name(),
                seed.warningThreshold().multiply(SUFFICIENT_FACTOR)
        );
        LocalDate inbound = LocalDate.now().minusDays(7);
        LocalDate expiration = LocalDate.now().plusDays(30);

        InventoryBatch batch = new InventoryBatch(
                ingredient, quantity, null, inbound, expiration);
        inventoryBatchRepository.save(batch);
    }

    /**
     * PurchaseOrder 시드 — 시안 카드의 "마지막 발주" 일자 표시용.
     * demo user 발주 이력이 1건이라도 있으면 스킵 (사용자 데이터 보호).
     * sim, 2026-06-01.
     */
    private void seedPurchaseOrdersIfMissing(User demo) {
        if (purchaseOrderRepository.existsByUserId(demo.getId())) {
            return;
        }
        int seeded = 0;
        for (var entry : SEED_LAST_ORDER.entrySet()) {
            String name = entry.getKey();
            LocalDate orderedAt = entry.getValue();

            var ingredientOpt = ingredientRepository
                    .findByUserIdAndName(demo.getId(), name);
            if (ingredientOpt.isEmpty()) {
                continue;
            }
            Ingredient ingredient = ingredientOpt.get();

            // 적당한 시연용 수치 — 권장 재고와 동일 수량을 단가 1원 가정으로 시드
            BigDecimal qty = ingredient.getWarningThreshold();
            BigDecimal unitPrice = BigDecimal.ONE;
            BigDecimal total = qty.multiply(unitPrice);

            PurchaseOrder po = PurchaseOrder.builder()
                    .user(demo)
                    .ingredient(ingredient)
                    .orderedAt(orderedAt)
                    .quantity(qty)
                    .baseUnit(ingredient.getBaseUnit())
                    .unitPrice(unitPrice)
                    .totalAmount(total)
                    .supplier("데모 거래처")
                    .memo("DataInitializer 시드")
                    .status(PurchaseStatus.CONFIRMED)
                    .build();
            purchaseOrderRepository.save(po);
            seeded++;
        }
        if (seeded > 0) {
            log.info("[DataInitializer] PurchaseOrder 시드 {}건 생성", seeded);
        }
    }

    private record IngredientSeed(
            String name,
            String baseUnit,
            String kamisCategory,
            BigDecimal warningThreshold,
            String uiCategory,   // sim, 2026-06-01 — kim ProductData.category 매핑
            String imageUrl      // sim, 2026-06-01 — kim ProductData.image 매핑
    ) {}
}
