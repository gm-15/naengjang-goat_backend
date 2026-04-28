-- price_records 테이블 DDL (식자재왕 크롤러 + 네이버/쿠팡 API 공통)
-- main.py 의 ensure_schema() 가 없는 컬럼을 자동 ALTER 로 추가하므로,
-- 이미 기본 테이블만 있어도 동작합니다. 본 파일은 최종 목표 스키마 문서용.

CREATE TABLE IF NOT EXISTS `price_records` (
  `id`           INT          NOT NULL AUTO_INCREMENT,
  `source`       VARCHAR(100) DEFAULT NULL     COMMENT '수집처 (예: 식자재왕_채소/과일, 네이버, 쿠팡)',
  `product_name` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '상품명',
  `price`        INT          NOT NULL           COMMENT '판매가 (원 단위 정수)',
  `currency`     VARCHAR(10)  DEFAULT 'KRW'      COMMENT '통화',
  `is_discount`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '할인 여부 (0/1)',
  `product_url`  TEXT                            COMMENT '상품 상세 URL',
  `fetched_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '수집 시각',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
