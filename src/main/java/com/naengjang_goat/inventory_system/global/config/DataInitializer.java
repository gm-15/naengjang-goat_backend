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

    // KAMIS dailyPriceByCategoryList item_name → (baseUnit, kamisCategory, warningThreshold)
    // 이름은 KAMIS API 응답 item_name 과 정확히 일치해야 매칭됨
    private static final List<IngredientSeed> SEEDS = List.of(
            // 채소류 (cat=200)
            new IngredientSeed("배추",   "g",  "VEGETABLES", new BigDecimal("3000")),
            new IngredientSeed("양파",   "g",  "VEGETABLES", new BigDecimal("2000")),
            new IngredientSeed("마늘",   "g",  "VEGETABLES", new BigDecimal("500")),
            new IngredientSeed("대파",   "g",  "VEGETABLES", new BigDecimal("500")),
            new IngredientSeed("무",     "g",  "VEGETABLES", new BigDecimal("2000")),
            new IngredientSeed("건고추", "g",  "VEGETABLES", new BigDecimal("300")),
            // 수산물 (cat=600)
            new IngredientSeed("고등어", "g",  "SEAFOOD", new BigDecimal("500")),
            new IngredientSeed("명태",   "g",  "SEAFOOD", new BigDecimal("500")),
            new IngredientSeed("오징어", "g",  "SEAFOOD", new BigDecimal("300")),
            // 과일류 (cat=400)
            new IngredientSeed("사과",   "개", "FRUITS", new BigDecimal("10")),
            new IngredientSeed("배",     "개", "FRUITS", new BigDecimal("5")),
            // 곡물류 (cat=100)
            new IngredientSeed("쌀",     "g",  "GRAINS", new BigDecimal("5000"))
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(DEMO_USERNAME)) {
            log.info("[DataInitializer] 데모 유저 이미 존재 — 스킵");
            return;
        }

        // 데모 유저 생성
        User demo = userRepository.save(new User(
                DEMO_USERNAME,
                passwordEncoder.encode(DEMO_PASSWORD),
                "데모점주",
                Role.OWNER
        ));
        log.info("[DataInitializer] 데모 유저 생성 → id={}", demo.getId());

        // KAMIS 매칭용 ingredient 시드 생성
        int count = 0;
        for (IngredientSeed seed : SEEDS) {
            boolean exists = ingredientRepository
                    .findByUserIdAndName(demo.getId(), seed.name()).isPresent();
            if (!exists) {
                Ingredient ingredient = new Ingredient(
                        demo, seed.name(), seed.baseUnit(), seed.warningThreshold());
                ingredient.setKamisCategory(seed.kamisCategory());
                ingredientRepository.save(ingredient);
                count++;
            }
        }
        log.info("[DataInitializer] ingredient 시드 {}종 생성 완료", count);
        log.info("[DataInitializer] 로그인 정보 → username={} / password={}", DEMO_USERNAME, DEMO_PASSWORD);
    }

    private record IngredientSeed(
            String name,
            String baseUnit,
            String kamisCategory,
            BigDecimal warningThreshold
    ) {}
}
