"""
recipes.json → DB(`recipe_template`, `recipe_template_bom`) 적재.

목적:
  사장님이 첫 가입 시 식당 카테고리(한식/양식/중식/일식/기타)를 선택하면
  해당 카테고리의 메뉴들을 자동으로 본인 menu·recipe 로 복사할 수 있게,
  공용 레시피 템플릿 테이블을 미리 채워둔다.

흐름:
  1. recipe_template / recipe_template_bom 테이블 생성 (없으면)
  2. recipes.json 메뉴별로 키워드 기반 카테고리 자동 분류
  3. UNIQUE(source, source_recipe_idx) 로 idempotent INSERT
"""

import json
import os
import sys

import pymysql

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


db_config = {
    'host':     os.environ.get('DB_HOST', '127.0.0.1'),
    'port':     int(os.environ.get('DB_PORT', '3306')),
    'user':     os.environ.get('DB_USER', 'root'),
    'password': os.environ.get('DB_PASSWORD', ''),
    'db':       os.environ.get('DB_NAME', 'naengjang_goat_db'),
    'charset':  'utf8mb4',
}

if not db_config['password']:
    print("❌ 환경변수 DB_PASSWORD 가 비어 있습니다. .env 또는 쉘에서 지정하세요.")
    sys.exit(1)

JSON_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "recipes.json")


# ---------------------------------------------------------------------------
# 카테고리 분류 (메뉴명 키워드 기반)
# ---------------------------------------------------------------------------
# 우선순위: 강한 일식 > 강한 양식 > 강한 중식 > 한식 > 그 외(OTHER)

JAPANESE_KEYWORDS = [
    "우동", "라멘", "돈가스", "스시", "사시미", "텐푸라", "야끼", "타코야끼",
    "카츠", "오므라이스", "오니기리", "나베", "회덮", "초밥",
]
WESTERN_KEYWORDS = [
    "파스타", "피자", "스테이크", "그라탕", "뇨끼", "오믈렛", "샐러드",
    "샌드위치", "햄버거", "리조또", "라자냐", "케밥", "텐더", "치오피노",
    "커리", "치킨커리", "오일", "치즈", "수프", "스튜",
]
CHINESE_KEYWORDS = [
    "짜장", "짬뽕", "탕수", "마라", "깐풍", "마파", "양장피", "꿔바로우",
    "동파", "차오몐", "딤섬", "게살소스",
]
KOREAN_KEYWORDS = [
    "김치", "된장", "고추장", "떡", "만두", "국밥", "찌개", "비빔", "잡채",
    "갈비", "불고기", "삼겹살", "무침", "나물", "죽", "순두부", "깍두기",
    "묵", "칼국수", "수제비", "누룽지", "황태", "쫄면", "마늘쫑", "전",
    "탕", "곰탕", "수육", "구이", "조림", "볶음밥",
]


def classify(menu_name: str) -> str:
    name = menu_name or ""
    for kw in JAPANESE_KEYWORDS:
        if kw in name:
            return "JAPANESE"
    for kw in WESTERN_KEYWORDS:
        if kw in name:
            return "WESTERN"
    for kw in CHINESE_KEYWORDS:
        if kw in name:
            return "CHINESE"
    for kw in KOREAN_KEYWORDS:
        if kw in name:
            return "KOREAN"
    return "OTHER"


# ---------------------------------------------------------------------------
# DDL
# ---------------------------------------------------------------------------
DDL_TEMPLATE = """
CREATE TABLE IF NOT EXISTS `recipe_template` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `category`          VARCHAR(20)  NOT NULL,
  `menu_name`         VARCHAR(100) NOT NULL,
  `source`            VARCHAR(20)  NOT NULL DEFAULT '식자재왕',
  `source_recipe_idx` INT          NOT NULL,
  `image_url`         TEXT,
  `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_idx` (`source`, `source_recipe_idx`),
  INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"""

DDL_BOM = """
CREATE TABLE IF NOT EXISTS `recipe_template_bom` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `template_id`       BIGINT       NOT NULL,
  `ingredient_name`   VARCHAR(255) NOT NULL,
  `quantity`          INT          NULL,
  `unit`              VARCHAR(10)  NULL,
  `raw_product_gno`   VARCHAR(64)  NULL,
  `product_price`     INT          NULL,
  `is_discount`       TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_template` (`template_id`),
  CONSTRAINT `fk_rtb_template`
    FOREIGN KEY (`template_id`) REFERENCES `recipe_template`(`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"""


def ensure_schema(conn):
    with conn.cursor() as cur:
        cur.execute(DDL_TEMPLATE)
        cur.execute(DDL_BOM)
    conn.commit()


# ---------------------------------------------------------------------------
# 적재
# ---------------------------------------------------------------------------
def import_recipes(conn, recipes: list[dict]):
    inserted_template = 0
    skipped_template = 0
    inserted_bom = 0

    with conn.cursor() as cur:
        for rec in recipes:
            idx = rec.get("recipeIdx")
            menu_name = rec.get("menuName") or ""
            image_url = rec.get("imageUrl") or None
            if not idx or not menu_name:
                continue

            category = classify(menu_name)

            # 1) 템플릿 INSERT (UNIQUE 키로 중복 방지)
            cur.execute(
                """
                INSERT IGNORE INTO recipe_template
                  (category, menu_name, source, source_recipe_idx, image_url)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (category, menu_name, "식자재왕", idx, image_url),
            )
            if cur.rowcount > 0:
                template_id = cur.lastrowid
                inserted_template += 1
            else:
                skipped_template += 1
                cur.execute(
                    "SELECT id FROM recipe_template "
                    "WHERE source=%s AND source_recipe_idx=%s",
                    ("식자재왕", idx),
                )
                row = cur.fetchone()
                if not row:
                    continue
                template_id = row[0]
                # 기존 row 의 BOM 은 그대로 두고 skip
                continue

            # 2) BOM INSERT
            for b in rec.get("bom", []):
                cur.execute(
                    """
                    INSERT INTO recipe_template_bom
                      (template_id, ingredient_name, quantity, unit,
                       raw_product_gno, product_price, is_discount)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        template_id,
                        b.get("ingredientName") or "",
                        b.get("quantity"),
                        b.get("unit"),
                        b.get("rawProductGno"),
                        b.get("productPrice"),
                        1 if b.get("isDiscount") else 0,
                    ),
                )
                inserted_bom += 1

    conn.commit()
    return inserted_template, skipped_template, inserted_bom


def report(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT category, COUNT(*) FROM recipe_template GROUP BY category ORDER BY 2 DESC")
        print("\n[카테고리별 템플릿 수]")
        for cat, cnt in cur.fetchall():
            print(f"  {cat:<10} {cnt}개")

        cur.execute("SELECT COUNT(*) FROM recipe_template")
        total = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM recipe_template_bom")
        bom_total = cur.fetchone()[0]
        print(f"\n총 템플릿: {total}개 / BOM rows: {bom_total}개")

        cur.execute(
            "SELECT category, menu_name FROM recipe_template "
            "ORDER BY category, menu_name LIMIT 20"
        )
        print("\n[샘플 20개]")
        for cat, name in cur.fetchall():
            print(f"  [{cat:<8}] {name}")


def main():
    if not os.path.exists(JSON_PATH):
        print(f"❌ {JSON_PATH} 없음. 먼저 `python recipe.py` 실행하세요.")
        sys.exit(1)

    with open(JSON_PATH, "r", encoding="utf-8") as f:
        recipes = json.load(f)
    print(f"📂 recipes.json 로드: {len(recipes)}개 메뉴")

    conn = pymysql.connect(**db_config)
    try:
        ensure_schema(conn)
        print("🔧 스키마 보장 완료")

        ins_t, skip_t, ins_b = import_recipes(conn, recipes)
        print(f"\n✅ 적재 완료")
        print(f"   recipe_template:     신규 {ins_t}개 · 기존 skip {skip_t}개")
        print(f"   recipe_template_bom: 신규 {ins_b}개")

        report(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
