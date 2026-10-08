/** 서버 UnitConverter와 같은 호환 단위. 구매 단가를 재고 단위로 바꾸지 않는다. */
const UNITS = {
  g: { group: "weight", factor: 1 },
  kg: { group: "weight", factor: 1000 },
  ml: { group: "volume", factor: 1 },
  L: { group: "volume", factor: 1000 },
  개: { group: "count", factor: 1 },
} as const;

export type StockUnit = keyof typeof UNITS;

/** JSON으로 보내는 십진수 그대로 계산하여 포장 크기·환산의 반올림 오차를 피한다. */
function decimal(value: number): { digits: bigint; scale: number } {
  const [mantissa, exponent = "0"] = String(value).split("e");
  const [integer, fraction = ""] = mantissa.split(".");
  const scale = fraction.length - Number(exponent);
  const digits = BigInt(integer + fraction);
  return scale >= 0 ? { digits, scale } : { digits: digits * 10n ** BigInt(-scale), scale: 0 };
}

/** 양수에 대한 BigDecimal HALF_UP. 반환값은 지정 소수 자릿수의 정수이다. */
function roundDecimal(digits: bigint, scale: number, places: number): bigint {
  if (scale <= places) return digits * 10n ** BigInt(places - scale);
  const divisor = 10n ** BigInt(scale - places);
  return (digits + divisor / 2n) / divisor;
}

export function compatiblePurchaseUnits(inventoryUnit: string): StockUnit[] {
  const normalized = inventoryUnit.toLowerCase() === "l" ? "L" : inventoryUnit.toLowerCase();
  const target = UNITS[normalized as StockUnit];
  return target ? (Object.keys(UNITS) as StockUnit[]).filter((unit) => UNITS[unit].group === target.group) : [];
}

export function preferredPurchaseUnit(inventoryUnit: string): StockUnit | undefined {
  const units = compatiblePurchaseUnits(inventoryUnit);
  return units.includes("kg") ? "kg" : units.includes("L") ? "L" : units[0];
}

export function previewPurchase(
  quantity: number,
  purchaseUnit: string,
  inventoryUnit: string,
  unitPrice: number,
  packageSize?: number,
  packageUnit?: string,
): { expectedQuantityBase: number; totalAmount: number } {
  if (!Number.isFinite(quantity) || quantity <= 0 || quantity > 9999999.999)
    throw new Error("구매 수량은 0보다 크고 9,999,999.999 이하여야 합니다.");
  const quantityDecimal = decimal(quantity);
  if (quantityDecimal.scale > 3)
    throw new Error("구매 수량은 소수 셋째 자리까지 입력할 수 있습니다.");
  if (!Number.isFinite(unitPrice) || unitPrice < 0 || unitPrice > 99999999.99)
    throw new Error("구매 단가는 0 이상 99,999,999.99원 이하여야 합니다.");
  const priceDecimal = decimal(unitPrice);
  if (priceDecimal.scale > 2)
    throw new Error("구매 단가는 소수 둘째 자리까지 입력할 수 있습니다.");
  const sourceUnit = purchaseUnit === "포장" ? packageUnit : purchaseUnit;
  if (!sourceUnit || !compatiblePurchaseUnits(inventoryUnit).includes(sourceUnit as StockUnit))
    throw new Error("재고 단위와 호환되는 구매 단위를 선택해 주세요.");
  let sourceDigits = quantityDecimal.digits;
  let sourceScale = quantityDecimal.scale;
  if (purchaseUnit === "포장") {
    if (!Number.isInteger(quantity)) throw new Error("포장 구매 수량은 정수로 입력해 주세요.");
    if (!Number.isFinite(packageSize) || (packageSize ?? 0) <= 0)
      throw new Error("포장 한 개의 실제 크기를 입력해 주세요.");
    const sizeDecimal = decimal(packageSize as number);
    sourceDigits *= sizeDecimal.digits;
    sourceScale += sizeDecimal.scale;
  }
  const targetUnit = inventoryUnit.toLowerCase() === "l" ? "L" : inventoryUnit.toLowerCase();
  // 서버는 다른 단위 변환 시 기준 단위와 대상 단위에서 각각 소수 셋째 자리 반올림.
  const source = UNITS[sourceUnit as StockUnit];
  const target = UNITS[targetUnit as StockUnit];
  const expectedThousandths = source === target
    ? roundDecimal(sourceDigits, sourceScale, 3)
    : roundDecimal(roundDecimal(sourceDigits * BigInt(source.factor), sourceScale, 3), target.factor === 1000 ? 6 : 3, 3);
  if (expectedThousandths <= 0n || expectedThousandths > 9999999999n)
    throw new Error("입고 예정 수량의 범위를 확인해 주세요. 재고 단위로 0보다 커야 합니다.");
  const totalDigits = quantityDecimal.digits * priceDecimal.digits;
  const totalScale = quantityDecimal.scale + priceDecimal.scale;
  const exceedsTotal = totalScale >= 2
    ? totalDigits > 999999999999n * 10n ** BigInt(totalScale - 2)
    : totalDigits * 10n ** BigInt(2 - totalScale) > 999999999999n;
  if (exceedsTotal) throw new Error("구매 총액은 9,999,999,999.99원 이하여야 합니다.");
  const expectedQuantityBase = Number(expectedThousandths) / 1000;
  const totalAmount = Number(roundDecimal(totalDigits, totalScale, 2)) / 100;
  return { expectedQuantityBase, totalAmount };
}
