package com.naengjang_goat.inventory_system.user.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.domain.RecipeTemplate;
import com.naengjang_goat.inventory_system.menu.domain.RecipeTemplateBom;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.menu.repository.RecipeBomRepository;
import com.naengjang_goat.inventory_system.menu.repository.RecipeTemplateRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.dto.OnboardRequest;
import com.naengjang_goat.inventory_system.user.dto.OnboardResponse;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 사장님 온보딩 서비스.
 *
 * 카테고리 선택 → recipe_template 조회 → 점주 menu + RecipeBom 자동 생성.
 *
 * 재료 매칭 전략:
 *   - ingredientName 으로 기존 재료 검색 (findByUserIdAndName)
 *   - 없으면 새 Ingredient 생성 (baseUnit="g", warningThreshold=null)
 *   - 새로 생성된 재료는 응답 newIngredients 목록에 포함
 *
 * BOM requiredQuantity:
 *   - 1 (placeholder). 식자재왕 BOM은 패키지 단위라 1인분 소모량 아님.
 *   - 사장님이 직접 수정해야 원가 계산이 정확해짐.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardService {

    private final RecipeTemplateRepository templateRepository;
    private final MenuRepository menuRepository;
    private final RecipeBomRepository recipeBomRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;

    @Transactional
    public OnboardResponse onboard(Long userId, OnboardRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 없음: " + userId));

        List<String> categories = request.categories();
        List<RecipeTemplate> templates = templateRepository.findAllByCategoryInWithBom(categories);

        if (templates.isEmpty()) {
            log.warn("[Onboard] 해당 카테고리 템플릿 없음 userId={} categories={}", userId, categories);
            return new OnboardResponse(0, 0, List.of());
        }

        int createdMenus = 0;
        int createdBom = 0;
        List<String> newIngredients = new ArrayList<>();

        for (RecipeTemplate template : templates) {
            // 1. 메뉴 생성 (가격 0 — 사장님 직접 설정)
            Menu menu = new Menu(user, template.getMenuName(), 0);
            menuRepository.save(menu);
            createdMenus++;

            // 2. BOM 복사
            for (RecipeTemplateBom bomRow : template.getBomList()) {
                String ingredientName = bomRow.getIngredientName();

                // 기존 재료 검색, 없으면 신규 생성
                Ingredient ingredient = ingredientRepository
                        .findByUserIdAndName(userId, ingredientName)
                        .orElseGet(() -> {
                            newIngredients.add(ingredientName);
                            Ingredient newOne = new Ingredient(user, ingredientName, "g", null);
                            return ingredientRepository.save(newOne);
                        });

                // requiredQuantity = 1 (placeholder — 사장님 수정 필요)
                RecipeBom bom = new RecipeBom(menu, ingredient, BigDecimal.ONE, ingredient.getBaseUnit());
                recipeBomRepository.save(bom);
                createdBom++;
            }
        }

        log.info("[Onboard] userId={} categories={} → 메뉴 {}건 BOM {}건 신규재료 {}건",
                userId, categories, createdMenus, createdBom, newIngredients.size());

        return new OnboardResponse(createdMenus, createdBom, newIngredients);
    }
}
