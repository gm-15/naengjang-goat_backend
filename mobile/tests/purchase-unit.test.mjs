import test from "node:test";
import assert from "node:assert/strict";
import { compatiblePurchaseUnits, preferredPurchaseUnit, previewPurchase } from "../src/lib/purchase-unit.ts";

test("재고 단위별 호환 단위와 구매 기본 단위를 선택한다", () => {
  assert.deepEqual(compatiblePurchaseUnits("g"), ["g", "kg"]);
  assert.deepEqual(compatiblePurchaseUnits("ml"), ["ml", "L"]);
  assert.deepEqual(compatiblePurchaseUnits("l"), ["ml", "L"]);
  assert.deepEqual(compatiblePurchaseUnits("개"), ["개"]);
  assert.deepEqual(compatiblePurchaseUnits("봉지"), []);
  assert.equal(preferredPurchaseUnit("g"), "kg");
  assert.equal(preferredPurchaseUnit("ml"), "L");
  assert.equal(preferredPurchaseUnit("개"), "개");
});

test("구매 단가를 유지하면서 kg 구매를 g 재고로 환산한다", () => {
  assert.deepEqual(previewPurchase(1.5, "kg", "g", 8000), { expectedQuantityBase: 1500, totalAmount: 12000 });
  assert.deepEqual(previewPurchase(250, "g", "kg", 2), { expectedQuantityBase: 0.25, totalAmount: 500 });
});

test("포장 수량과 포장당 크기는 재고에 곱하고 가격은 포장 수량에만 곱한다", () => {
  assert.deepEqual(previewPurchase(3, "포장", "g", 15000, 2, "kg"), { expectedQuantityBase: 6000, totalAmount: 45000 });
  assert.deepEqual(previewPurchase(2, "포장", "개", 5000, 30, "개"), { expectedQuantityBase: 60, totalAmount: 10000 });
});

test("ml/L 변환과 서버의 소수 셋째 자리 반올림을 따른다", () => {
  assert.deepEqual(previewPurchase(2, "L", "ml", 1000), { expectedQuantityBase: 2000, totalAmount: 2000 });
  assert.deepEqual(previewPurchase(1, "포장", "L", 500, 2.675, "ml"), { expectedQuantityBase: 0.003, totalAmount: 500 });
  assert.deepEqual(previewPurchase(1, "포장", "g", 0, 0.0005, "g"), { expectedQuantityBase: 0.001, totalAmount: 0 });
  assert.deepEqual(previewPurchase(1, "포장", "g", 0, 1.0055, "g"), { expectedQuantityBase: 1.006, totalAmount: 0 });
});

test("포장 수량은 정수이고 포장 크기를 반드시 확인한다", () => {
  assert.throws(() => previewPurchase(1.5, "포장", "g", 1000, 2, "kg"), /정수/);
  assert.throws(() => previewPurchase(1, "포장", "g", 1000), /단위/);
  assert.throws(() => previewPurchase(1, "포장", "g", 1000, 0, "g"), /실제 크기/);
});

test("재고와 다른 종류의 단위나 지원하지 않는 단위를 막는다", () => {
  assert.throws(() => previewPurchase(1, "L", "g", 1000), /호환/);
  assert.throws(() => previewPurchase(1, "포장", "ml", 1000, 2, "kg"), /호환/);
  assert.throws(() => previewPurchase(1, "봉지", "개", 1000), /호환/);
});

test("음수/NaN/무한대 및 API 수량·단가의 소수 자릿수를 검증한다", () => {
  for (const quantity of [0, -1, NaN, Infinity]) assert.throws(() => previewPurchase(quantity, "g", "g", 1000), /구매 수량/);
  for (const price of [-1, NaN, Infinity]) assert.throws(() => previewPurchase(1, "g", "g", price), /구매 단가/);
  assert.throws(() => previewPurchase(1.0001, "g", "g", 1000), /셋째 자리/);
  assert.throws(() => previewPurchase(1, "g", "g", 1000.001), /둘째 자리/);
});

test("환산 후 0이 되거나 DB 허용 수량/총액을 넘으면 막는다", () => {
  assert.throws(() => previewPurchase(1, "포장", "g", 1000, 0.0001, "g"), /입고 예정/);
  assert.throws(() => previewPurchase(10000, "kg", "g", 0), /입고 예정/);
  assert.throws(() => previewPurchase(1000, "g", "g", 99999999.99), /구매 총액/);
  assert.deepEqual(previewPurchase(9999999.999, "개", "개", 0), { expectedQuantityBase: 9999999.999, totalAmount: 0 });
});
