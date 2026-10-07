package com.naengjang_goat.inventory_system.workflow;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosUploadRepository extends JpaRepository<PosUpload, Long> {
  Optional<PosUpload> findByUserIdAndFileHash(Long userId, String hash);

  Optional<PosUpload> findByUserIdAndBusinessDate(Long userId, LocalDate date);

  List<PosUpload> findByUserIdAndBusinessDateBetweenOrderByBusinessDateAsc(
      Long userId, LocalDate from, LocalDate to);

  Optional<PosUpload> findFirstByUserIdOrderByBusinessDateDescCreatedAtDesc(Long userId);
}
