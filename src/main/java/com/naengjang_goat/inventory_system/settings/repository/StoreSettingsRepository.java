package com.naengjang_goat.inventory_system.settings.repository;

import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {

    Optional<StoreSettings> findByUserId(Long userId);
}
