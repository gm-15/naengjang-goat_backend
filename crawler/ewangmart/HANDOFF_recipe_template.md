# 식자재왕 레시피 템플릿 — park 인수 문서 (sim → park)

> 작성: 2026-05-02 sim
> 대상: park (메인 백엔드)
> 관련 브랜치: `feat/sikjajaewang-recipe-crawler`
> 관련 Step: plan_park_0429_01.md §3 Step 6 (식자재왕 BOM 크롤링)

---

## 1. 의도

**사장님 onboarding 시 카테고리 선택 → 해당 카테고리의 메뉴들이 자동으로 본인 `menu`·`recipe` 에 복사** 되도록 하기 위해, 식자재왕 레시피를 **공용 템플릿 라이브러리** 로 미리 적재함.

```
사장님 가입
   ↓
식당 정보 입력 (카테고리: KOREAN / WESTERN / CHINESE / JAPANESE / OTHER)
   ↓
[park 미구현] 선택한 카테고리 → recipe_template 조회 → menu·recipe 자동 복사
```

---

## 2. 추가된 테이블 2개

park 의 `menu` / `recipe` 도메인과 분리. 사장님 데이터가 아닌 **공용 라이브러리**.

```sql
CREATE TABLE recipe_template (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  category          VARCHAR(20)  NOT NULL,   -- KOREAN / WESTERN / CHINESE / JAPANESE / OTHER
  menu_name         VARCHAR(100) NOT NULL,
  source            VARCHAR(20)  NOT NULL DEFAULT '식자재왕',
  source_recipe_idx INT          NOT NULL,   -- 식자재왕 idx (재실행 시 idempotent 보장)
  image_url         TEXT,
  created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_idx (source, source_recipe_idx),
  INDEX idx_category (category)
);

CREATE TABLE recipe_template_bom (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  template_id     BIGINT       NOT NULL,
  ingredient_name VARCHAR(255) NOT NULL,    -- 식자재왕 상품명 그대로 (예: "[푸디스트전용] 중국산 냉동 홍합살 1kg")
  quantity        INT          NULL,        -- 상품명에서 파싱한 무게(g)
  unit            VARCHAR(10)  NULL,        -- "g" 통일
  raw_product_gno VARCHAR(64)  NULL,        -- 식자재왕 gno
  product_price   INT          NULL,
  is_discount     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  INDEX idx_template (template_id),
  CONSTRAINT fk_rtb_template
    FOREIGN KEY (template_id) REFERENCES recipe_template(id) ON DELETE CASCADE
);
```

`crawler/ewangmart/import_template.py` 가 `CREATE TABLE IF NOT EXISTS` 로 자동 생성. Flyway 등록은 별도(park 결정).

---

## 3. 적재 결과 — 68개 메뉴 / 1,222 BOM rows

| 카테고리 | 메뉴 수 | 예시 |
|----------|---------|------|
| KOREAN   | 28 | 사골순두부 · 떡갈비 · 황태채마늘쫑볶음 · 묵국밥 |
| OTHER    | 20 | 버터치킨커리 · 멕시코풍장각구이 · 피쉬케밥 |
| WESTERN  | 13 | 치오피노 · 단호박크림옹심이뇨끼 · 해물냉파스타 |
| CHINESE  |  5 | 게살소스볶음밥 · 깐풍가지 · 볶음짬뽕 |
| JAPANESE |  2 | 돈코츠라멘 · 반반오므라이스 |

분류 방식: 메뉴명 키워드 기반 자동 (우선순위 일식 > 양식 > 중식 > 한식 > OTHER).

**분류 정확도 한계** — 발표 직전 수동 수정 후보:
- "떡갈비난자완스" → KOREAN ("떡" 키워드 매칭, 사실 중식·퓨전)
- "누룽지크림새우" → KOREAN ("누룽지" 매칭, 양식·퓨전이 더 정확)
- "멕시코풍장각구이" → KOREAN ("구이" 매칭, OTHER 가 정확)

수정 SQL 예:
```sql
UPDATE recipe_template SET category='OTHER' WHERE menu_name LIKE '멕시코%';
UPDATE recipe_template SET category='WESTERN' WHERE menu_name LIKE '%크림새우%';
```

---

## 4. ⚠️ 알아야 할 한계

식자재왕 레시피는 **정량 정보가 없음** (이 메뉴 만들 때 양파 50g 같은 단위 표기 없음). 단지 "이 메뉴에 필요한 상품 묶음" 만 제공.

따라서 `recipe_template_bom` 의 `quantity` 는 **상품명에서 파싱한 무게값** (예: "1kg" → 1000g, "900mL x 2" → 1800g). 메뉴 1인분당 소모량이 아닌 **상품 패키지 단위**.

영향:
- park 의 `RecipeBom.requiredQuantity` 의미와 다름. **그대로 복사하면 BOM 이 과대 계상됨.**
- 옵션: park 의 onboard API 에서 `quantity / 100` 또는 임의 비율로 축소, 또는 `quantity = NULL` 로 두고 사장님 직접 입력 유도.
- 또는: `recipe_template_bom.quantity` 를 무시하고 `ingredient_name` 만 활용해 BOM "재료 풀" 로만 사용.

이 부분은 **park 설계 결정 필요**.

---

## 5. park 가 만들어야 할 것 — onboard API 제안

```
POST /users/onboard
Header: X-User-Id: {userId}
Body: { "categories": ["KOREAN", "WESTERN"] }

처리:
  1. SELECT * FROM recipe_template WHERE category IN (?, ?)
  2. 각 row → menu INSERT (user_id, name=menu_name, image_url 매핑)
  3. SELECT * FROM recipe_template_bom WHERE template_id = ?
  4. 각 BOM row →
       a. ingredientName 으로 IngredientRepository.findByNameAndUserId 시도
       b. 매칭 실패 → 새 Ingredient 생성 (또는 unmatched 로그)
       c. RecipeBom INSERT (menu_id, ingredient_id, requiredQuantity, unit)

응답: { "createdMenus": 41, "createdBom": 850, "unmatchedIngredients": [...] }
```

`ingredient_name` 매칭은 IngredientMatcher 로직 재사용 가능.

---

## 6. 데이터 재크롤링 / 재적재

**식자재왕 사이트 변경 시 갱신 흐름**:
```bash
cd crawler/ewangmart
python recipe.py            # recipes.json 새로 생성
python import_template.py   # DB 반영 (UNIQUE 키로 중복 skip, 신규만 INSERT)
```

`recipes.json` 은 git 에 포함 (287KB) — park 가 직접 import 도 가능.

---

## 7. 산출물 요약

| 항목 | 위치 |
|------|------|
| 크롤러 | `crawler/ewangmart/recipe.py` |
| 데이터 (raw) | `crawler/ewangmart/recipes.json` |
| DB 적재 스크립트 | `crawler/ewangmart/import_template.py` |
| 본 문서 | `crawler/ewangmart/HANDOFF_recipe_template.md` |
| DB | `naengjang_goat_db.recipe_template` · `recipe_template_bom` |

---

## 8. park 확인 요청 사항

1. `recipe_template` / `recipe_template_bom` 테이블이 **별도 라이브러리** 임 OK? (park 의 menu/recipe 와 무관)
2. **quantity 의미 차이** (상품 패키지 단위 vs 메뉴 1인분 소모량) — 어떻게 처리할지
3. **onboard API** (`POST /users/onboard`) park 가 만들어줄 수 있나?
4. **category 분류 정확도** — 발표 직전 수정 권한 (sim 이 SQL UPDATE 직접 / park API 통해)?

확인 후 회신 부탁드립니다.
