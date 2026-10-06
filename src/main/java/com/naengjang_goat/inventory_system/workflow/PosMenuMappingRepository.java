package com.naengjang_goat.inventory_system.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosMenuMappingRepository extends JpaRepository<PosMenuMapping, Long> {
  Optional<PosMenuMapping> findByUserIdAndPosCode(Long userId, String code);

  List<PosMenuMapping> findAllByUserId(Long userId);
}
