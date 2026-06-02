package com.naengjang_goat.inventory_system.global.config;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
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
import java.util.List;

/**
 * 앱 기동 시 데모 시드 데이터 생성.
 *
 * demo 유저가 없을 때만 1회 실행.
 *
 * 생성 내용:
 *   - 데모 계정: username=demo / password=demo1234 / ownerName=데모점주
 *   - KAMIS item_name 과 1:1 매칭되는 ingredient 11종
 *     (KamisPriceProcessor.findByName() 매칭 대상)
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
    private static final List<IngredientSeed> SEEDS = List.of(
            // 채소류 (cat=200)
            new IngredientSeed("배추",     "g",  "VEGETABLES", new BigDecimal("3000"), "채소", IMG_VEG),
            new IngredientSeed("양파",     "g",  "VEGETABLES", new BigDecimal("2000"), "채소", IMG_VEG),
            new IngredientSeed("마늘",     "g",  "VEGETABLES", new BigDecimal("500"),  "채소", IMG_VEG),
            new IngredientSeed("대파",     "g",  "VEGETABLES", new BigDecimal("500"),  "채소", IMG_VEG),
            new IngredientSeed("무",       "g",  "VEGETABLES", new BigDecimal("2000"), "채소", IMG_VEG),
            new IngredientSeed("건고추",   "g",  "VEGETABLES", new BigDecimal("300"),  "채소", IMG_VEG),
            // 수산물 (cat=600)
            new IngredientSeed("고등어",   "g",  "SEAFOOD",    new BigDecimal("500"),  "기타", IMG_FISH),
            new IngredientSeed("명태",     "g",  "SEAFOOD",    new BigDecimal("500"),  "기타", IMG_FISH),
            new IngredientSeed("오징어",   "g",  "SEAFOOD",    new BigDecimal("300"),  "기타", IMG_FISH),
            // 과일류 (cat=400)
            new IngredientSeed("사과",     "개", "FRUITS",     new BigDecimal("10"),   "기타", IMG_FRUIT),
            new IngredientSeed("배",       "개", "FRUITS",     new BigDecimal("5"),    "기타", IMG_FRUIT),
            // 곡물류 (cat=100)
            new IngredientSeed("쌀",       "g",  "GRAINS",     new BigDecimal("5000"), "기타", IMG_GRAIN),
            // 축산물 (EKAPE) — EkapeApiClient 키워드 매핑 대상
            new IngredientSeed("삼겹살",   "g",  "LIVESTOCK",  new BigDecimal("500"),  "육류", IMG_MEAT),
            new IngredientSeed("목심",     "g",  "LIVESTOCK",  new BigDecimal("300"),  "육류", IMG_MEAT),
            new IngredientSeed("닭고기",   "g",  "LIVESTOCK",  new BigDecimal("500"),  "육류", IMG_MEAT),
            new IngredientSeed("등심",     "g",  "LIVESTOCK",  new BigDecimal("300"),  "육류", IMG_MEAT),
            // sim 추가 (2026-06-01) — 시안 /lowest-price 노출 항목 + 소스/양념 카테고리 활성화
            new IngredientSeed("튀김가루", "g",  "PROCESSED",  new BigDecimal("1000"), "기타",      IMG_PROC),
            new IngredientSeed("식용유",   "ml", "PROCESSED",  new BigDecimal("2000"), "기타",      IMG_PROC),
            new IngredientSeed("진간장",   "ml", "PROCESSED",  new BigDecimal("1000"), "소스/양념", IMG_PROC)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 데모 유저 조회 or 생성
        User demo = userRepository.findByUsername(DEMO_USERNAME).orElseGet(() -> {
            User created = userRepository.save(new User(
                    DEMO_USERNAME,
                    passwordEncoder.encode(DEMO_PASSWORD),
                    "데모점주",
                    Role.OWNER
            ));
            log.info("[DataInitializer] 데모 유저 생성 → id={}", created.getId());
            return created;
        });

        // KAMIS/EKAPE 매칭용 ingredient 시드 — 미존재 항목만 생성 (멱등)
        // sim, 2026-06-01 — 기존 row 에 category/image_url 점진 보충도 처리
        int created = 0;
        int backfilled = 0;
        for (IngredientSeed seed : SEEDS) {
            var existingOpt = ingredientRepository
                    .findByUserIdAndName(demo.getId(), seed.name());
            if (existingOpt.isEmpty()) {
                Ingredient ingredient = new Ingredient(
                        demo, seed.name(), seed.baseUnit(), seed.warningThreshold());
                ingredient.setKamisCategory(seed.kamisCategory());
                ingredient.setCategory(seed.uiCategory());     // sim, 2026-06-01
                ingredient.setImageUrl(seed.imageUrl());       // sim, 2026-06-01
                ingredientRepository.save(ingredient);
                created++;
            } else {
                // 기존 row — 새로 추가된 필드만 보충 (null 인 경우, 사용자 수정 보존)
                Ingredient existing = existingOpt.get();
                boolean dirty = false;
                if (existing.getCategory() == null) {
                    existing.setCategory(seed.uiCategory());
                    dirty = true;
                }
                if (existing.getImageUrl() == null) {
                    existing.setImageUrl(seed.imageUrl());
                    dirty = true;
                }
                if (dirty) {
                    ingredientRepository.save(existing);
                    backfilled++;
                }
            }
        }

        if (created > 0 || backfilled > 0) {
            log.info("[DataInitializer] ingredient 시드 생성={}, 점진보충={}", created, backfilled);
        } else {
            log.info("[DataInitializer] ingredient 시드 모두 이미 존재 — 스킵");
        }
        log.info("[DataInitializer] 로그인 정보 → username={} / password={}", DEMO_USERNAME, DEMO_PASSWORD);
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
