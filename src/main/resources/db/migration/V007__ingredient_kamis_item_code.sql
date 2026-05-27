-- V007: KAMIS item_code 기반 매칭을 위한 컬럼 추가
-- 이름(봄배추, 고랭지배추 등) 대신 코드로 매핑해 배치 안정성 향상
ALTER TABLE ingredient
    ADD COLUMN kamis_item_code VARCHAR(10) NULL COMMENT 'KAMIS 품목 코드 (item_code). 설정 시 이름 대신 코드로 매칭';

CREATE INDEX idx_ingredient_kamis_item_code ON ingredient (kamis_item_code);
