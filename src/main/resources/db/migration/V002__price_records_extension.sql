-- =============================================================================
-- V002 — price_records 확장 (UC-CORE-2 v0.4)
-- =============================================================================
-- 근거: plan_park_0423_02.md §3-2, plan_park_0426_01.md §3-1
--
-- 전제: V001 (CREATE TABLE) 이 Flyway 에 의해 먼저 적용된 상태
--
-- 주의:
--   - JPA ddl-auto=update 는 GENERATED COLUMN, UNIQUE 인덱스를 자동 생성하지 않음
--   - 본 SQL 은 Flyway 가 자동 적용 (애플리케이션 기동 시)
-- =============================================================================

-- 1. 추가 컬럼 4개 (unit_price_per_kg 는 GENERATED 로 별도 추가)
ALTER TABLE price_records
  ADD COLUMN ingredient_id  BIGINT       NULL
    COMMENT 'Ingredient.id 매핑 결과 (실패 시 NULL, 매칭 배치가 채움)',
  ADD COLUMN weight_grams   INT          NULL
    COMMENT 'product_name 파싱 결과 (실패 시 NULL). 액체는 LiquidDensity 적용 후 g 단위',
  ADD COLUMN raw_product_id VARCHAR(100) NOT NULL DEFAULT ''
    COMMENT '소스별 상품 ID (식자재왕=gno, 네이버=productId). NOT NULL 강제',
  ADD COLUMN image_url      TEXT         NULL
    COMMENT '상품 썸네일 URL (UI 시안 노출용)';

-- 2. GENERATED STORED 컬럼 — DB 가 자동 계산
--    sim 의 정합성 우려 + park 의 INDEX 성능 요구를 동시 충족
ALTER TABLE price_records
  ADD COLUMN unit_price_per_kg BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN weight_grams > 0
           THEN price * 1000 / weight_grams
           ELSE NULL
      END
    ) STORED
    COMMENT '원/kg 정규화. DB가 INSERT/UPDATE 시 자동 계산. 직접 INSERT/UPDATE 불가';

-- 3. 조회용 INDEX (ingredient + 최신순)
CREATE INDEX idx_pr_ingredient_fetched
  ON price_records (ingredient_id, fetched_at DESC);

-- 4. 정렬·필터용 INDEX (최저가 정렬)
CREATE INDEX idx_pr_ingredient_unitprice
  ON price_records (ingredient_id, unit_price_per_kg);

-- 5. 중복 방지 UNIQUE (append-only 전환 동시 적용)
--    sim 제안 변경: product_url → raw_product_id 기반 (URL 변동성 회피)
--    DATE(fetched_at) 로 일 1건 누적 → 가격 추이 축적 가능
ALTER TABLE price_records
  ADD UNIQUE KEY uk_pr_source_rawid_date (source, raw_product_id, (DATE(fetched_at)));

-- =============================================================================
-- 적용 후 검증 쿼리:
--   SHOW CREATE TABLE price_records;
--   SHOW INDEX FROM price_records;
-- =============================================================================
