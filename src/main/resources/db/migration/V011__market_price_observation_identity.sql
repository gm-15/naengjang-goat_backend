-- 기존 KAMIS 행은 채널/관측일을 복원할 수 없으므로 삭제·추측 변환하지 않는다.
-- 새 수집 자료만 명시적인 가격 기준 및 관측 식별자를 갖는다.
ALTER TABLE market_price
    ADD COLUMN price_basis VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN observation_key VARCHAR(64) NULL,
    ADD COLUMN kind_name VARCHAR(100) NULL,
    ADD COLUMN grade VARCHAR(100) NULL;

UPDATE market_price SET price_basis = 'RETAIL' WHERE source = 'EKAPE';
UPDATE market_price
SET price_basis = CASE WHEN wholesale_price IS NOT NULL THEN 'WHOLESALE' ELSE 'RETAIL' END
WHERE source = 'DEMO';

-- NULL인 기존 식별자는 보존된다. 신규 KAMIS의 동일 관측 재실행/동시 실행만 제약한다.
CREATE UNIQUE INDEX uk_market_price_observation
    ON market_price (ingredient_id, source, price_basis, reported_date, observation_key);
