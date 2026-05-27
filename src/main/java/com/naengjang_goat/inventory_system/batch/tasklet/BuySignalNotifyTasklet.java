package com.naengjang_goat.inventory_system.batch.tasklet;

import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.global.service.FcmService;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.domain.KamisCategory;
import com.naengjang_goat.inventory_system.pricing.service.KamisPriceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KAMIS 수집 완료 후 buySignal=true 재료를 사용자별로 집계해 FCM 알림 발송.
 *
 * 동작 순서:
 *  1. 전체 Ingredient 조회
 *  2. 재료별 KamisPriceCalculator로 buySignal 판정
 *  3. 사용자별 buySignal 재료 목록 집계
 *  4. 사용자 fcmToken 있으면 FCM 발송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuySignalNotifyTasklet implements Tasklet {

    private final IngredientRepository ingredientRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final KamisPriceCalculator kamisPriceCalculator;
    private final FcmService fcmService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 사용자별 buySignal 재료 이름 목록
        Map<Long, List<String>> userSignals = new LinkedHashMap<>();
        Map<Long, String> userFcmTokens = new LinkedHashMap<>();

        List<Ingredient> allIngredients = ingredientRepository.findAll();

        for (Ingredient ingredient : allIngredients) {
            if (ingredient.getKamisCategory() == null) continue;

            KamisCategory category;
            try {
                category = KamisCategory.valueOf(ingredient.getKamisCategory());
            } catch (IllegalArgumentException e) {
                continue;
            }

            var kamisDto = kamisPriceCalculator.buildKamis(ingredient.getId());
            if (kamisDto == null || kamisDto.getMonthAvg() == null || kamisDto.getCurrentPricePerKg() == null) {
                continue;
            }

            double threshold = category.buySignalThreshold;
            boolean buySignal = kamisDto.getCurrentPricePerKg() < kamisDto.getMonthAvg() * (1 - threshold);

            if (buySignal) {
                Long userId = ingredient.getUser().getId();
                userSignals.computeIfAbsent(userId, k -> new ArrayList<>()).add(ingredient.getName());

                String fcmToken = ingredient.getUser().getFcmToken();
                if (fcmToken != null && !fcmToken.isBlank()) {
                    userFcmTokens.put(userId, fcmToken);
                }
            }
        }

        log.info("[BUY-SIGNAL-NOTIFY] buySignal 재료 보유 사용자 수: {}", userSignals.size());

        // 사용자별 FCM 발송
        for (Map.Entry<Long, List<String>> entry : userSignals.entrySet()) {
            Long userId = entry.getKey();
            List<String> items = entry.getValue();
            String fcmToken = userFcmTokens.get(userId);

            if (fcmToken == null) {
                log.debug("[BUY-SIGNAL-NOTIFY] userId={} fcmToken 없음 — skip", userId);
                continue;
            }

            String title = "오늘 사면 유리한 재료가 있어요 🛒";
            String body = String.join(", ", items) + " — 30일 평균보다 저렴합니다";
            fcmService.send(fcmToken, title, body);
            log.info("[BUY-SIGNAL-NOTIFY] userId={} 알림 발송: {}", userId, items);
        }

        contribution.incrementWriteCount(userSignals.size());
        return RepeatStatus.FINISHED;
    }
}
