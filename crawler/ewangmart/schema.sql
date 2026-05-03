-- price_records 테이블 — 식자재왕 크롤러 INSERT 컬럼 기준 (참고용)
--
-- ⚠️ 실 마이그레이션은 백엔드 Flyway (src/main/resources/db/migration/V*.sql) 가 책임집니다.
-- 본 파일은 크롤러가 **읽고 INSERT 할 컬럼 스펙** 을 문서화하기 위한 참고용입니다.
-- 신규 환경 셋업은 Java 앱을 한 번 기동하여 Flyway 가 V001/V002 등을 적용하도록 하세요.

CREATE TABLE IF NOT EXISTS `price_records` (
  `id`             INT          NOT NULL AUTO_INCREMENT,
  `source`         VARCHAR(100) DEFAULT NULL          COMMENT '수집처 (예: 식자재왕_채소/과일, 네이버, 쿠팡)',
  `product_name`   VARCHAR(255) NOT NULL DEFAULT ''   COMMENT '상품명',
  `raw_product_id` VARCHAR(64)  NOT NULL              COMMENT '소스 내부 상품 ID (식자재왕=gno, 네이버=productId)',
  `weight_grams`   INT          NULL                  COMMENT '파싱된 총 무게(g). 실패 시 NULL',
  `price`          INT          NOT NULL              COMMENT '판매가 (원 단위 정수)',
  `currency`       VARCHAR(10)  DEFAULT 'KRW'         COMMENT '통화',
  `is_discount`    TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '할인 여부 (0/1)',
  `product_url`    TEXT                               COMMENT '상품 상세 URL',
  `fetched_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '수집 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_raw_date` (`source`, `raw_product_id`, ((CAST(`fetched_at` AS DATE))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 백엔드 추가 컬럼 (크롤러 INSERT 대상 아님):
--   ingredient_id     INT  NULL    -- IngredientMatcher 배치가 사후 채움
--   unit_price_per_kg BIGINT GENERATED ALWAYS AS (
--       CASE WHEN weight_grams > 0 THEN price * 1000 / weight_grams ELSE NULL END
--   ) STORED                       -- DB 자동 계산
