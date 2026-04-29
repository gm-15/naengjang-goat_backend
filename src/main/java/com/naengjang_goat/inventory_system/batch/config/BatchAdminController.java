package com.naengjang_goat.inventory_system.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * KAMIS 배치 수동 트리거 (개발·데모용).
 *
 * POST /admin/batch/kamis/run
 *   → kamisPriceJob 즉시 실행, 결과 반환
 *
 * ※ 프로덕션에서는 IP 제한 또는 @Profile("dev") 로 제한 권장
 */
@Slf4j
@RestController
@RequestMapping("/admin/batch")
@RequiredArgsConstructor
public class BatchAdminController {

    private final JobLauncher jobLauncher;
    private final Job kamisPriceJob;

    @PostMapping("/kamis/run")
    public ResponseEntity<Map<String, Object>> runKamisBatch() {
        try {
            JobExecution execution = jobLauncher.run(kamisPriceJob, BatchTimestampParams.now());
            log.info("[BATCH-ADMIN] kamisPriceJob triggered: executionId={} status={}",
                    execution.getId(), execution.getStatus());
            return ResponseEntity.ok(Map.of(
                    "executionId", execution.getId(),
                    "status", execution.getStatus().toString(),
                    "startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : "null"
            ));
        } catch (Exception e) {
            log.error("[BATCH-ADMIN] kamisPriceJob trigger failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
