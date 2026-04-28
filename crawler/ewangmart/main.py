import os
import sys
import re
import time
import pymysql

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from webdriver_manager.chrome import ChromeDriverManager

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
    'db':       os.environ.get('DB_NAME', 'nangjanggoat'),
    'charset':  'utf8mb4',
}

if not db_config['password']:
    print("❌ 환경변수 DB_PASSWORD 가 설정되어 있지 않습니다. .env 또는 쉘에서 지정하세요.")
    sys.exit(1)


BASE_URL = "https://www.ewangmart.com"
SALES_URL = f"{BASE_URL}/goods/sales.do"

# 주방/일회용품(16), 생활/위생용품(17) 제외
CATEGORIES = [
    (3,  "채소/과일"),
    (9,  "냉장식품"),
    (7,  "가공식품"),
    (8,  "냉동식품"),
    (12, "유지류/조미류"),
    (11, "장류/소스류"),
    (10, "김치/반찬"),
    (5,  "축산/난류"),
    (13, "생수/음료"),
    (14, "유제품"),
    (6,  "수산/건어"),
    (4,  "곡류/견과"),
    (15, "과자/안주"),
]

_SKIP_TOKENS = {
    '할인', '좋아요', '담기', '상온', '냉장', '냉동',
    '신규', '품절', 'SALE', 'NEW', '쿠폰', '무료배송',
}


def ensure_schema(conn):
    with conn.cursor() as cursor:
        cursor.execute(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = %s AND table_name = 'price_records'
            """,
            (db_config['db'],),
        )
        cols = {row[0] for row in cursor.fetchall()}

        if 'product_name' not in cols:
            print("🔧 product_name 컬럼 추가 중...")
            cursor.execute(
                "ALTER TABLE price_records "
                "ADD COLUMN product_name VARCHAR(255) NOT NULL DEFAULT '' AFTER source"
            )

        if 'is_discount' not in cols:
            print("🔧 is_discount 컬럼 추가 중...")
            cursor.execute(
                "ALTER TABLE price_records "
                "ADD COLUMN is_discount TINYINT(1) NOT NULL DEFAULT 0 AFTER currency"
            )

        conn.commit()


def reset_db(conn):
    with conn.cursor() as cursor:
        print("🧹 price_records 비우는 중...")
        cursor.execute("TRUNCATE TABLE price_records;")
    conn.commit()
    print("✅ 초기화 완료")


def wait_for_goods(driver, timeout=15):
    WebDriverWait(driver, timeout).until(
        lambda d: len(d.find_elements(By.CSS_SELECTOR, "#listGoodsAjaxArea > *")) > 0
    )


def get_total_pages(driver):
    max_page_idx = 0
    elements = driver.find_elements(By.CSS_SELECTOR, "#paggingArea a, #paggingArea button")
    for el in elements:
        onclick = el.get_attribute("onclick") or ""
        for m in re.finditer(r"goGoodsList\((\d+)\)", onclick):
            max_page_idx = max(max_page_idx, int(m.group(1)))
        text = (el.text or "").strip()
        if text.isdigit():
            max_page_idx = max(max_page_idx, int(text) - 1)
    return max_page_idx + 1


def go_to_page(driver, page_idx):
    driver.execute_script(
        "document.getElementById('listGoodsAjaxArea').innerHTML='';"
        "goGoodsList(arguments[0]);",
        page_idx,
    )
    wait_for_goods(driver)
    time.sleep(0.5)


def extract_items(driver, debug_dump_path=None):
    raw = driver.execute_script(
        r"""
        return Array.from(document.querySelectorAll('#listGoodsAjaxArea > li')).map(li => {
            const pickAll = (sel) => Array.from(li.querySelectorAll(sel))
                .map(e => (e.textContent || '').trim())
                .filter(t => t.length > 0);
            return {
                outerHTML: li.outerHTML,
                text: li.textContent || '',
                titleCandidates: pickAll(
                    '.goods-title, .goods-name, .goods-tit, .tit, .name, ' +
                    '.goods-txt-box .txt, a[href*="detail.do"] span, strong'
                ),
                priceCandidates: pickAll(
                    '.price, .goods-price, .sale-price, .price-sale, ' +
                    '.amount, .cost, em.price-num, strong.price-num'
                ),
            };
        });
        """
    ) or []

    if debug_dump_path and raw:
        with open(debug_dump_path, "w", encoding="utf-8") as f:
            f.write(raw[0]["outerHTML"])
        print(f"🪵 디버그 저장: {debug_dump_path}")

    results = []
    for item in raw:
        html = item.get("outerHTML", "")
        text = item.get("text", "")

        gno_match = re.search(r'gno=(\d+)', html)
        if not gno_match:
            continue
        gno = gno_match.group(1)

        name = ""
        for t in item.get("titleCandidates", []):
            if len(t) < 3 or '원' in t or t in _SKIP_TOKENS:
                continue
            name = t
            break
        if not name:
            lines = [l.strip() for l in text.splitlines() if len(l.strip()) > 2]
            lines = [l for l in lines if l not in _SKIP_TOKENS and '원' not in l]
            if lines:
                name = lines[0]
        name = name.replace('\u00a0', ' ').replace('\t', ' ')
        name = re.sub(r'\s+', ' ', name).strip()[:250]

        prices = []
        for t in item.get("priceCandidates", []):
            for m in re.finditer(r'([\d,]+)', t):
                try:
                    p = int(m.group(1).replace(',', ''))
                    if 100 <= p <= 500000 and p != 999999:
                        prices.append(p)
                except ValueError:
                    pass
        if not prices:
            for n in re.findall(r'([\d,]+)\s*원', text):
                try:
                    p = int(n.replace(',', ''))
                    if 100 <= p <= 500000 and p != 999999:
                        prices.append(p)
                except ValueError:
                    pass
        price_val = min(prices) if prices else 0

        if name and price_val > 0:
            results.append((gno, name, price_val))
    return results


def collect_discount_gnos(driver):
    """sales.do 페이지를 순회하며 할인 상품 gno 집합을 수집."""
    print("\n🏷️  할인 상품 목록 수집 시작")
    driver.get(SALES_URL)
    try:
        wait_for_goods(driver)
    except Exception:
        print("   ⚠️ sales.do 로드 실패 → 할인 플래그 없이 진행")
        return set()

    total = get_total_pages(driver)
    print(f"   총 {total}페이지")
    gnos = set()
    for page_idx in range(total):
        if page_idx > 0:
            try:
                go_to_page(driver, page_idx)
            except Exception:
                print(f"   ⚠️ 페이지 {page_idx + 1} 실패 → 건너뜀")
                continue
        htmls = driver.execute_script(
            "return Array.from(document.querySelectorAll('#listGoodsAjaxArea > li'))"
            ".map(li => li.innerHTML);"
        ) or []
        for html in htmls:
            for m in re.finditer(r'gno=(\d+)', html):
                gnos.add(m.group(1))
        print(f"   [{page_idx + 1}/{total}] 누적 {len(gnos)}개")
    print(f"✅ 할인상품 {len(gnos)}개 수집 완료")
    return gnos


def crawl_category(driver, cate_id, cate_name, discount_gnos, cursor, seen_gnos, first_dump=False):
    url = f"{BASE_URL}/goods/category.do?cate={cate_id}"
    source = f"식자재왕_{cate_name}"
    print(f"\n📂 [{cate_name}] cate={cate_id}")
    driver.get(url)
    try:
        wait_for_goods(driver)
    except Exception:
        print("   ⚠️ 로드 실패 / 빈 카테고리 → 건너뜀")
        return 0

    total = get_total_pages(driver)
    print(f"   총 {total}페이지")
    saved = 0

    for page_idx in range(total):
        if page_idx > 0:
            try:
                go_to_page(driver, page_idx)
            except Exception:
                print(f"   ⚠️ 페이지 {page_idx + 1} 실패 → 건너뜀")
                continue

        dump_path = "debug_first_item.html" if (first_dump and page_idx == 0) else None
        items = extract_items(driver, debug_dump_path=dump_path)

        page_saved = 0
        for gno, name, price in items:
            if gno in seen_gnos:
                continue
            seen_gnos.add(gno)
            is_discount = 1 if gno in discount_gnos else 0
            product_url = f"{BASE_URL}/goods/detail.do?gno={gno}"
            cursor.execute(
                "INSERT INTO price_records "
                "(source, product_name, price, currency, is_discount, product_url, fetched_at) "
                "VALUES (%s, %s, %s, %s, %s, %s, NOW())",
                (source, name, price, 'KRW', is_discount, product_url),
            )
            page_saved += 1
            saved += 1
        print(f"   [{page_idx + 1}/{total}] 저장 {page_saved}개 (카테고리 누적 {saved})")

    return saved


def crawl_all():
    options = Options()
    options.add_argument("--window-size=1400,900")
    driver = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=options,
    )

    conn = pymysql.connect(**db_config)
    conn.autocommit(True)
    cursor = conn.cursor()

    try:
        discount_gnos = collect_discount_gnos(driver)

        seen_gnos = set()
        grand_total = 0

        for idx, (cate_id, cate_name) in enumerate(CATEGORIES):
            saved = crawl_category(
                driver, cate_id, cate_name,
                discount_gnos, cursor, seen_gnos,
                first_dump=(idx == 0),
            )
            grand_total += saved

        cursor.execute("SELECT COUNT(*) FROM price_records WHERE is_discount = 1")
        discount_hits = cursor.fetchone()[0]

        print(f"\n🎉 전체 완료")
        print(f"   총 상품: {grand_total}개 (중복 제거 후)")
        print(f"   할인 상품: {discount_hits}개 (플래그 ON)")
        print(f"   할인 gno 중 미매칭: {len(discount_gnos) - discount_hits}개")

    finally:
        cursor.close()
        conn.close()
        driver.quit()


if __name__ == "__main__":
    conn = pymysql.connect(**db_config)
    try:
        ensure_schema(conn)
        reset_db(conn)
    finally:
        conn.close()
    crawl_all()
