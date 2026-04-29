-- V004: 점주 영업 설정 테이블
-- 작성일: 2026-04-29 | 담당: park
-- 용도: 발주 알림 타이밍 + 소진 예정일 계산의 기준값 저장

-- [주의] store_settings는 Flyway가 생성하지만 users FK는 JPA ddl-auto가 먼저
-- 실행된 이후 존재함. FK 제약은 JPA 엔티티(@OneToOne)로 관리하고,
-- Flyway에서는 FK 없이 테이블만 생성 후 UNIQUE 인덱스만 건다.
CREATE TABLE IF NOT EXISTS store_settings
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    open_time     TIME        NOT NULL COMMENT '영업 시작 시각 (예: 11:00:00)',
    close_time    TIME        NOT NULL COMMENT '영업 종료 시각 (예: 22:00:00)',
    order_day     VARCHAR(10) NOT NULL COMMENT '발주 요일 (MON/TUE/WED/THU/FRI/SAT/SUN)',
    inventory_day VARCHAR(10) NOT NULL COMMENT '재고 실사 요일',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ss_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '점주 영업 설정 (발주 요일·영업시간)';
