-- =============================================================================
-- V001 — price_records 기본 테이블 생성 (sim 베이스 스키마)
-- =============================================================================
-- 근거: crawler/ewangmart/schema.sql (sim 작성, 8컬럼 기본 구조)
--
-- Flyway 적용 순서:
--   V001 (이 파일) → V002 (park ALTER 확장)
--
-- 이미 테이블이 존재하면 IF NOT EXISTS 로 건너뜀.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `price_records` (
  `id`           INT          NOT NULL AUTO_INCREMENT,
  `source`       VARCHAR(100) DEFAULT NULL
    COMMENT '수집처 (예: 식자재왕_채소/과일, 네이버, 쿠팡)',
  `product_name` VARCHAR(255) NOT NULL DEFAULT ''
    COMMENT '상품명',
  `price`        INT          NOT NULL
    COMMENT '판매가 (원 단위 정수)',
  `currency`     VARCHAR(10)  DEFAULT 'KRW'
    COMMENT '통화',
  `is_discount`  TINYINT(1)   NOT NULL DEFAULT 0
    COMMENT '할인 여부 (0/1)',
  `product_url`  TEXT
    COMMENT '상품 상세 URL',
  `fetched_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP
    COMMENT '수집 시각',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
