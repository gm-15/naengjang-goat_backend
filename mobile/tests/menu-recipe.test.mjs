import test from "node:test";
import assert from "node:assert/strict";
import { menuRecipePayload } from "../src/lib/menu-recipe.ts";

test("POS 한 메뉴의 소모량을 기본 단위로 저장하고 소수 수량을 보존한다", () => {
  assert.deepEqual(menuRecipePayload("  제육볶음  ", "9000", [
    { ingredientId: 1, quantity: "150", unit: "g" },
    { ingredientId: 2, quantity: ".125", unit: "개" },
  ]), {
    name: "제육볶음", price: 9000, recipe: [
      { ingredientId: 1, requiredQuantity: 150, unit: "g" },
      { ingredientId: 2, requiredQuantity: 0.125, unit: "개" },
    ],
  });
});

test("같은 재료 중복 등록과 재료 미선택은 판매 차감 전에 막는다", () => {
  assert.throws(() => menuRecipePayload("메뉴", "9000", [
    { ingredientId: 1, quantity: "150", unit: "g" },
    { ingredientId: 1, quantity: "50", unit: "g" },
  ]), /같은 재료/);
  assert.throws(() => menuRecipePayload("메뉴", "9000", [{ ingredientId: null, quantity: "150", unit: "g" }]), /재료를 선택/);
  assert.throws(() => menuRecipePayload("메뉴", "9000", []), /재료는/);
});

test("재고 저장 정밀도보다 작은 값과 범위 초과 소모량은 허용하지 않는다", () => {
  for (const quantity of ["0", "-1", "0.0001", "10000000", "150.1234", "NaN", "1e3"]) {
    assert.throws(() => menuRecipePayload("메뉴", "9000", [{ ingredientId: 1, quantity, unit: "g" }]), /소모량/);
  }
  assert.equal(menuRecipePayload("메뉴", "0", [{ ingredientId: 1, quantity: "0.001", unit: "g" }]).recipe[0].requiredQuantity, 0.001);
  assert.equal(menuRecipePayload("메뉴", "9000", [{ ingredientId: 1, quantity: "9999999.999", unit: "g" }]).recipe[0].requiredQuantity, 9999999.999);
});

test("실제 가격을 정수 원 단위로 입력하고 메뉴명이 필요하다", () => {
  const rows = [{ ingredientId: 1, quantity: "150", unit: "g" }];
  for (const price of ["", "-1", "9000.5", "NaN", "2147483648"]) assert.throws(() => menuRecipePayload("메뉴", price, rows), /판매가/);
  assert.throws(() => menuRecipePayload("  ", "9000", rows), /메뉴명/);
  assert.throws(() => menuRecipePayload("메뉴", "9000", [{ ...rows[0], unit: "봉지" }]), /단위/);
});
