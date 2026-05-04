-- V006: market_price 에 source 컬럼 추가 (KAMIS / EKAPE 구분)
-- 기존 행은 모두 KAMIS 배치로 적재된 것이므로 DEFAULT 'KAMIS' 로 처리.
ALTER TABLE market_price
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'KAMIS';
