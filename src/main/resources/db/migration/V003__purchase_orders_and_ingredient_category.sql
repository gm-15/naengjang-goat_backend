-- V003: UC-CORE-3 — 발주 이력 테이블
-- 작성일: 2026-04-28 | 담당: park
--
-- [주의] ingredient.kamis_category 컬럼 추가는 이 파일에서 처리하지 않음.
-- Flyway는 JPA ddl-auto보다 먼저 실행되므로, ALTER TABLE ingredient 시점에
-- ingredient 테이블이 아직 존재하지 않음 → JPA ddl-auto=update가 Ingredient
-- 엔티티의 @Column(name="kamis_category")를 보고 자동으로 컬럼을 추가함.

-- 발주 이력 테이블 생성
CREATE TABLE IF NOT EXISTS purchase_orders
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    ingredient_id BIGINT        NOT NULL,
    ordered_at    DATE          NOT NULL,
    quantity      DECIMAL(10, 3) NOT NULL,
    base_unit     VARCHAR(20)   NOT NULL,
    unit_price    DECIMAL(10, 2) NOT NULL,
    total_amount  DECIMAL(12, 2) NOT NULL,
    supplier      VARCHAR(100)  NOT NULL,
    memo          TEXT,
    status        VARCHAR(20)   NOT NULL DEFAULT 'CONFIRMED',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_po_user_date (user_id, ordered_at DESC),
    INDEX idx_po_ingredient (ingredient_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '발주 이력 (UC-SUP-8)';
