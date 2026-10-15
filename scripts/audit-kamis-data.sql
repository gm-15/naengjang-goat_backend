-- 읽기 전용. 확인할 DB를 먼저 선택한다. LEGACY는 삭제하지 않고 별도로 센다.
SELECT COUNT(*) AS kamis_stored_rows,
       COALESCE(SUM(price_basis <> 'LEGACY'), 0) AS trusted_kamis_rows,
       MAX(CASE WHEN price_basis <> 'LEGACY' THEN reported_date END) AS latest_trusted_date
FROM market_price WHERE source = 'KAMIS';

SELECT source, price_basis, COUNT(*) AS stored_rows,
       COUNT(DISTINCT ingredient_id) AS ingredients,
       COUNT(DISTINCT reported_date) AS observation_days,
       MIN(reported_date) AS first_date, MAX(reported_date) AS last_date
FROM market_price GROUP BY source, price_basis ORDER BY source, price_basis;

-- 신규 관측 중복은 0행이어야 한다. 과거 NULL 식별자는 이 검사의 대상이 아니다.
SELECT ingredient_id, source, price_basis, reported_date, observation_key, COUNT(*) AS copies
FROM market_price WHERE observation_key IS NOT NULL
GROUP BY ingredient_id, source, price_basis, reported_date, observation_key HAVING COUNT(*) > 1;

-- 잘못된 기준 필드, 누락된 식별자, 미래 관측일은 0행이어야 한다.
SELECT id, ingredient_id, price_basis, retail_price, wholesale_price, unit, reported_date
FROM market_price
WHERE source = 'KAMIS' AND price_basis <> 'LEGACY' AND (
    observation_key IS NULL OR unit <> 'kg'
    OR reported_date > DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR)
    OR (price_basis = 'WHOLESALE' AND (wholesale_price IS NULL OR retail_price IS NOT NULL))
    OR (price_basis = 'RETAIL' AND (retail_price IS NULL OR wholesale_price IS NOT NULL))
);
