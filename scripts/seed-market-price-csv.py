#!/usr/bin/env python3
"""팀원이 제공한 과거 시세 CSV를 시연용 DB의 market_price 에 적재한다.

적재 기준(반드시 읽을 것):
  - CSV 가격은 소매 기준이므로 price_basis='RETAIL' 로만 넣는다. 도매 수집 자료와 섞지 않는다.
  - 출처(source)는 CSV 값(KAMIS/EKAPE)을 그대로 쓴다.
  - 원 단위가 kg·100g 이 아닌 행(10kg·15kg·20kg·30kg 등)은 제외한다.
    kg 라벨 행 대비 1/10~1/20 수준이라 환산이 한 번 더 적용된 것으로 보이기 때문이다.
  - 같은 재료·출처·날짜에 여러 행이 있으면 중앙값 하나로 합친다.
    ⚠ CSV 에 품종·등급 컬럼이 없어 서로 다른 품종·등급이 섞였을 수 있다.
      합친 값은 "시연용 대표값"이며 공식 관측 1건이 아니다. observation_key 로 구분해 둔다.
  - 재적재는 같은 observation_key 를 지우고 다시 넣는다(멱등).

사용 예:
  python scripts/seed-market-price-csv.py --csv C:/path/market_price_ml_ready.csv \
      --database naengjang_goat_demo --user-id 1
  python scripts/seed-market-price-csv.py --database naengjang_goat_demo --remove
"""
import argparse
import collections
import csv
import os
import statistics
import sys

try:
    import pymysql
except ImportError:
    sys.exit("pymysql 이 필요합니다: python -m pip install PyMySQL")

OBSERVATION_KEY = "CSV_SEED"
KEEP_UNITS = {"kg", "100g"}


def load_rows(path):
    with open(path, encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    kept = [r for r in rows if r["original_unit"].lower() in KEEP_UNITS]
    grouped = collections.defaultdict(list)
    for row in kept:
        grouped[(row["ingredient_name"], row["source"], row["reported_date"])].append(int(row["price_per_kg"]))
    merged = [(name, source, date, round(statistics.median(prices)))
              for (name, source, date), prices in sorted(grouped.items())]
    return len(rows), len(rows) - len(kept), merged


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv")
    parser.add_argument("--database", required=True)
    parser.add_argument("--user-id", type=int, default=1, help="재료를 보유한 점주 id")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--db-user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--remove", action="store_true", help="적재한 시드만 삭제하고 종료")
    args = parser.parse_args()

    password = os.getenv("DB_PASSWORD")
    if password is None:
        sys.exit("DB_PASSWORD 환경변수를 설정하세요. 비밀번호를 인자로 넘기지 않습니다.")

    conn = pymysql.connect(host=args.host, port=args.port, user=args.db_user,
                           password=password, database=args.database, charset="utf8mb4")
    try:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM market_price WHERE observation_key=%s", (OBSERVATION_KEY,))
            removed = cur.rowcount
            if args.remove:
                conn.commit()
                print(f"시드 {removed}행 삭제")
                return
            if not args.csv:
                sys.exit("--csv 를 지정하세요")

            total, dropped, merged = load_rows(args.csv)
            cur.execute("SELECT name, id FROM ingredient WHERE user_id=%s", (args.user_id,))
            ids = {name: ingredient_id for name, ingredient_id in cur.fetchall()}

            inserted, unmatched = 0, collections.Counter()
            for name, source, date, price in merged:
                ingredient_id = ids.get(name)
                if ingredient_id is None:
                    unmatched[name] += 1
                    continue
                cur.execute(
                    "INSERT INTO market_price (ingredient_id, retail_price, reported_date, unit, source,"
                    " price_basis, observation_key) VALUES (%s,%s,%s,'kg',%s,'RETAIL',%s)",
                    (ingredient_id, str(price), date, source, OBSERVATION_KEY))
                inserted += 1
        conn.commit()
        print(f"CSV {total}행 → 박스 단위 제외 {dropped}행 → 병합 {len(merged)}행 → 적재 {inserted}행 (기존 시드 {removed}행 삭제)")
        if unmatched:
            print("재료 미존재로 건너뜀:", dict(unmatched))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
