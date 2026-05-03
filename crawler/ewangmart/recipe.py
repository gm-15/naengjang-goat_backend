"""
식자재왕 레시피 크롤러 — Step 6 (sim 영역).

목적: 식자재왕 `/etc/recipe.do` 의 레시피 목록을 순회하며
       각 레시피의 메뉴명 + 관련 상품 리스트(BOM 후보)를 추출.

출력: `recipes.json` — park 가 메뉴 매칭 후 RecipeBom 으로 import.

  [
    {
      "recipeIdx": 1113,
      "menuName": "치오피노",
      "imageUrl": "...",
      "bom": [
        {
          "ingredientName": "중국산 냉동 홍합살",
          "quantity": 1000,           # 상품명에서 파싱한 무게(g)
          "unit": "g",
          "rawProductGno": "164093",
          "productPrice": 10900,
          "isDiscount": false
        }, ...
      ]
    }
  ]

park API (`POST /menus/{menuId}/bom-batch`) 직접 호출은 menuId 매칭이 사장님 등록 메뉴
기준이라 자동화 불가 → JSON 파일로 넘긴 뒤 park 측에서 ingredient 매칭 + import.
"""

import json
import os
import re
import sys
import time
from typing import Optional

import pymysql  # noqa: F401  (현재 미사용. 향후 DB 직접 적재로 확장 시 사용)
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from webdriver_manager.chrome import ChromeDriverManager

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


BASE_URL = "https://www.ewangmart.com"
LIST_URL = f"{BASE_URL}/etc/recipe.do"

OUTPUT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "recipes.json")


# ---------------------------------------------------------------------------
# weight 파싱 (main.py 의 parse_weight_grams 동일 로직 — 재사용 위해 복사)
# ---------------------------------------------------------------------------
_UNIT_TO_GRAMS = {
    'kg': 1000.0,
    'g':  1.0,
    'l':  1000.0,   # 액체: 1g/mL 가정. 백엔드 LiquidDensity 가 보정.
    'ml': 1.0,
}
_PAT_VAL_UNIT_MULT = re.compile(
    r'(\d+(?:\.\d+)?)\s*(kg|g|l|ml)\b\s*(?:[x×*]\s*(\d+))?',
    re.IGNORECASE,
)
_PAT_MULT_VAL_UNIT = re.compile(
    r'(\d+)\s*[x×*]\s*(\d+(?:\.\d+)?)\s*(kg|g|l|ml)\b',
    re.IGNORECASE,
)


def parse_weight_grams(name: str) -> Optional[int]:
    if not name:
        return None
    s = name.replace(',', '').lower()
    candidates = []
    for m in _PAT_VAL_UNIT_MULT.finditer(s):
        v = float(m.group(1))
        unit = m.group(2).lower()
        mult = int(m.group(3)) if m.group(3) else 1
        g = v * _UNIT_TO_GRAMS[unit] * mult
        if 0 < g <= 100_000:
            candidates.append(g)
    for m in _PAT_MULT_VAL_UNIT.finditer(s):
        mult = int(m.group(1))
        v = float(m.group(2))
        unit = m.group(3).lower()
        g = v * _UNIT_TO_GRAMS[unit] * mult
        if 0 < g <= 100_000:
            candidates.append(g)
    return int(round(max(candidates))) if candidates else None


# ---------------------------------------------------------------------------
# 크롤링 유틸
# ---------------------------------------------------------------------------
def wait_for_recipe_list(driver, timeout=15):
    WebDriverWait(driver, timeout).until(
        lambda d: len(d.find_elements(By.CSS_SELECTOR, "#listRecipeAjaxArea li")) > 0
    )


def get_total_pages(driver) -> int:
    """페이지네이션 영역에서 총 페이지 수를 추정."""
    max_page = 1
    elems = driver.find_elements(
        By.CSS_SELECTOR, "#paggingArea a, .paging a, #paging a"
    )
    for el in elems:
        onclick = el.get_attribute("onclick") or ""
        m = re.search(r"callRecipeList\((\d+)\)", onclick)
        if m:
            max_page = max(max_page, int(m.group(1)))
        text = (el.text or "").strip()
        if text.isdigit():
            max_page = max(max_page, int(text))
    return max_page


def go_to_page(driver, page: int):
    driver.execute_script(
        "document.getElementById('listRecipeAjaxArea').innerHTML='';"
        "callRecipeList(arguments[0]);",
        page,
    )
    wait_for_recipe_list(driver)
    time.sleep(0.5)


def collect_recipe_indices(driver) -> list[int]:
    """현재 표시된 목록에서 레시피 idx 수집."""
    raw = driver.execute_script(
        "return Array.from(document.querySelectorAll("
        "'#listRecipeAjaxArea li a.goods-img-box-link'))"
        ".map(a => a.getAttribute('href'));"
    ) or []
    indices = []
    for href in raw:
        m = re.search(r'idx=(\d+)', href or '')
        if m:
            indices.append(int(m.group(1)))
    return indices


def extract_recipe_detail(driver, idx: int) -> Optional[dict]:
    """단일 레시피 상세 페이지 → 메뉴명 + 관련 상품 list 추출."""
    driver.get(f"{BASE_URL}/etc/recipe_detail.do?idx={idx}")
    try:
        WebDriverWait(driver, 10).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, "h3.title-type1")
        )
    except Exception:
        print(f"   ⚠️ idx={idx} 로드 실패")
        return None

    raw = driver.execute_script(r"""
        const titleEl = document.querySelector('h3.title-type1');
        const imgEl   = document.querySelector('.recipe-img img');
        const items = Array.from(document.querySelectorAll('ul.goods-wrap > li.goods-item')).map(li => {
            const link  = li.querySelector('a.goods-img-box-link, a.goods-info-box-link');
            const title = li.querySelector('.goods-title');
            const price = li.querySelector('.sale-price em, .goods-price em');
            const orig  = li.querySelector('.original-price em');
            const stateEls = Array.from(li.querySelectorAll('.goods-state-item'));
            return {
                href:  link  ? link.getAttribute('href')  : '',
                title: title ? title.textContent.replace(/\s+/g, ' ').trim() : '',
                price: price ? price.textContent.replace(/[^\d]/g, '') : '',
                isDiscount: !!orig,
                state: stateEls.map(e => e.textContent.trim()).join(',')
            };
        });
        return {
            menuName: titleEl ? titleEl.textContent.trim() : '',
            imageUrl: imgEl   ? imgEl.getAttribute('src')  : '',
            items: items
        };
    """) or {}

    menu_name = (raw.get("menuName") or "").strip()
    if not menu_name:
        return None

    bom = []
    for it in raw.get("items", []):
        href = it.get("href") or ""
        gno_match = re.search(r'gno=(\d+)', href)
        if not gno_match:
            continue
        gno = gno_match.group(1)

        title = it.get("title") or ""
        title = re.sub(r' ', ' ', title)         # nbsp 정리
        title = re.sub(r'\s+', ' ', title).strip()
        if not title:
            continue

        price_str = it.get("price") or ""
        price = int(price_str) if price_str.isdigit() else None

        weight = parse_weight_grams(title)
        bom.append({
            "ingredientName": title,
            "quantity": weight,        # 파싱 실패 시 None → park 가 sentinel 처리
            "unit": "g" if weight is not None else None,
            "rawProductGno": gno,
            "productPrice": price,
            "isDiscount": bool(it.get("isDiscount")),
        })

    image_url = raw.get("imageUrl") or ""
    if image_url.startswith("//"):
        image_url = "https:" + image_url

    return {
        "recipeIdx": idx,
        "menuName": menu_name,
        "imageUrl": image_url,
        "bom": bom,
    }


# ---------------------------------------------------------------------------
# 메인 흐름
# ---------------------------------------------------------------------------
def crawl(max_pages: Optional[int] = None) -> list[dict]:
    options = Options()
    options.add_argument("--window-size=1400,900")
    driver = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=options,
    )

    all_recipes: list[dict] = []
    seen_idx: set[int] = set()

    try:
        driver.get(LIST_URL)
        print("📚 식자재왕 레시피 목록 진입")
        wait_for_recipe_list(driver)

        total = get_total_pages(driver)
        print(f"   감지된 총 페이지: {total}")
        if max_pages:
            total = min(total, max_pages)
            print(f"   max_pages={max_pages} 적용 → {total} 페이지만 수집")

        # 1) 페이지마다 idx 수집
        all_indices: list[int] = []
        for p in range(1, total + 1):
            if p > 1:
                try:
                    go_to_page(driver, p)
                except Exception as e:
                    print(f"   ⚠️ 페이지 {p} 로드 실패: {e}")
                    continue
            indices = collect_recipe_indices(driver)
            new_count = 0
            for idx in indices:
                if idx not in seen_idx:
                    seen_idx.add(idx)
                    all_indices.append(idx)
                    new_count += 1
            print(f"   [{p}/{total}] idx 수집 {new_count}개 (누적 {len(all_indices)})")

        # 2) 상세 페이지 순회
        print(f"\n🍳 레시피 상세 {len(all_indices)}개 추출 시작")
        for i, idx in enumerate(all_indices, 1):
            try:
                rec = extract_recipe_detail(driver, idx)
            except Exception as e:
                print(f"   ⚠️ idx={idx} 예외: {e}")
                continue
            if rec is None:
                continue
            all_recipes.append(rec)
            if i % 5 == 0 or i == len(all_indices):
                print(f"   [{i}/{len(all_indices)}] {rec['menuName']} (BOM {len(rec['bom'])}개)")

    finally:
        driver.quit()

    return all_recipes


def save(recipes: list[dict], path: str = OUTPUT_FILE):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(recipes, f, ensure_ascii=False, indent=2)
    print(f"\n💾 저장 완료: {path}")
    print(f"   총 레시피: {len(recipes)}개")
    bom_total = sum(len(r["bom"]) for r in recipes)
    print(f"   총 BOM rows: {bom_total}개")
    parsed = sum(1 for r in recipes for b in r["bom"] if b["quantity"] is not None)
    print(f"   weight_grams 파싱 성공: {parsed}/{bom_total} ({100*parsed/bom_total:.1f}%)")


if __name__ == "__main__":
    # 인자 없으면 전체, "--pages N" 으로 N 페이지만
    max_pages = None
    if len(sys.argv) >= 3 and sys.argv[1] == "--pages":
        max_pages = int(sys.argv[2])
    recipes = crawl(max_pages=max_pages)
    save(recipes)
