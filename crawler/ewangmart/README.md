# 식자재왕몰 크롤러 (ewangmart)

`https://www.ewangmart.com` 카테고리 상품을 크롤링하여 공통 `price_records` 테이블에 적재합니다.
**주방/일회용품 · 생활/위생용품** 2개 카테고리는 제외. 할인상품(`/goods/sales.do` 노출 상품)은 `is_discount=1` 로 플래그가 찍힙니다.

## 수집 대상

13개 상위 카테고리: 채소/과일, 냉장식품, 가공식품, 냉동식품, 유지류/조미류, 장류/소스류, 김치/반찬, 축산/난류, 생수/음료, 유제품, 수산/건어, 곡류/견과, 과자/안주.

## 셋업

```bash
cd crawler/ewangmart
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS / Linux
source .venv/bin/activate

pip install -r requirements.txt
cp .env.example .env   # Windows: copy .env.example .env
# .env 파일을 열어 DB_PASSWORD 등 실제 값 입력
```

## DB 준비

기본 테이블이 없으면 먼저 생성:

```bash
mysql -u root -p nangjanggoat < schema.sql
```

- `product_name`, `is_discount` 컬럼은 `main.py` 실행 시 `ensure_schema()` 가 자동으로 추가합니다.

## 실행

```bash
python main.py
```

- 실행 시 `TRUNCATE price_records` 후 풀 스캔(덮어쓰기)합니다.
- 완료 소요시간: 카테고리 13개 기준 약 20~40분 (네트워크 상태에 따라 변동).
- 크롬 브라우저 창이 뜹니다(헤드리스 아님). 백그라운드로 돌리려면 `Options().add_argument("--headless=new")` 를 `main.py`의 `crawl_all()` 에 추가.

## 데이터 형식 (팀 공유 스펙)

| 필드 | 타입 | 예시 |
|------|------|------|
| `source` | VARCHAR(100) | `식자재왕_채소/과일` |
| `product_name` | VARCHAR(255) | `[서울우유] 365 1등급우유 1.8L` |
| `price` | INT | `3980` (판매가, 원) |
| `currency` | VARCHAR(10) | `KRW` |
| `is_discount` | TINYINT(1) | `0` / `1` |
| `product_url` | TEXT | `https://www.ewangmart.com/goods/detail.do?gno=86024` |
| `fetched_at` | DATETIME | `2026-04-23 18:30:12` |

네이버/쿠팡 API 어댑터도 동일한 컬럼 규약으로 INSERT 합니다. 상세 매핑 규칙은 팀 노션/스펙 문서 참고.

## 유의사항

- **재실행은 항상 풀 덮어쓰기**. 가격 히스토리 축적이 필요해지면 `TRUNCATE` → append-only + `(source, product_url, DATE(fetched_at))` UNIQUE 로 변경 필요.
- 사이트 DOM 변경 시 `extract_items()` 의 셀렉터 튜닝 필요.
- 첫 실행 때 `debug_first_item.html` 로 카드 HTML 스냅샷을 남겨둠 (git 에는 커밋하지 않음).
