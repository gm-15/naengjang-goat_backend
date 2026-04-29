package com.naengjang_goat.inventory_system.settings.service;

import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.dto.StoreSettingsRequest;
import com.naengjang_goat.inventory_system.settings.dto.StoreSettingsResponse;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점주 영업 설정 서비스.
 *
 * - GET /settings  : 설정 조회. 미설정 시 configured=false 응답 (예외 아님)
 * - PUT /settings  : upsert — 없으면 생성, 있으면 수정
 */
@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final StoreSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public StoreSettingsResponse getSettings(Long userId) {
        return settingsRepository.findByUserId(userId)
                .map(StoreSettingsResponse::from)
                .orElse(StoreSettingsResponse.empty());
    }

    @Transactional
    public StoreSettingsResponse upsertSettings(Long userId, StoreSettingsRequest req) {
        StoreSettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));
                    return new StoreSettings(user, req.openTime(), req.closeTime(),
                            req.orderDay(), req.inventoryDay());
                });

        // upsert — 기존 엔티티면 필드 덮어쓰기
        settings.setOpenTime(req.openTime());
        settings.setCloseTime(req.closeTime());
        settings.setOrderDay(req.orderDay());
        settings.setInventoryDay(req.inventoryDay());

        return StoreSettingsResponse.from(settingsRepository.save(settings));
    }

    /**
     * 다른 서비스(DailySalesService 등)에서 설정을 직접 조회할 때 사용.
     * 설정 미완료 시 IllegalStateException — 호출 전 설정 여부 확인 권장.
     */
    @Transactional(readOnly = true)
    public StoreSettings getSettingsOrThrow(Long userId) {
        return settingsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "영업 설정이 완료되지 않았습니다. PUT /settings 를 먼저 호출하세요. userId=" + userId));
    }
}
