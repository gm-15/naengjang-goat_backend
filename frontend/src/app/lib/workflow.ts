import type { QueryClient } from "@tanstack/react-query";
export const todayKorean = () =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul" }).format(
    new Date(),
  );
export const formatQuantity = (value: number) =>
  Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 3 });
export function invalidateOperations(qc: QueryClient): void {
  for (const key of [
    "batches",
    "low-stock",
    "lowest-top",
    "daily-report",
    "closing-recommendations",
    "purchase-orders",
    "purchase-summary",
  ]) {
    void qc.invalidateQueries({ queryKey: [key] });
  }
}
export function allowedUnits(base: string): string[] {
  if (["g", "kg"].includes(base)) return ["g", "kg"];
  if (["ml", "L", "l"].includes(base)) return ["ml", "L"];
  return [base];
}
export function quantityInBase(
  quantity: number,
  unit: string,
  base: string,
): number {
  const factors: Record<string, number> = {
    g: 1,
    kg: 1000,
    ml: 1,
    L: 1000,
    l: 1000,
    개: 1,
  };
  return (quantity * (factors[unit] ?? 1)) / (factors[base] ?? 1);
}
