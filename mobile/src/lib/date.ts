export function formatLocalDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function todayISO(): string {
  return formatLocalDate(new Date());
}

export function futureISO(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return formatLocalDate(d);
}

/** "YYYY-MM-DD" → 로컬 Date (UTC 파싱으로 하루 밀리는 문제 방지) */
export function parseLocalDate(s: string): Date {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

export function isWithinDays(dateStr: string, days: number): boolean {
  const target = parseLocalDate(dateStr);
  const diff = (target.getTime() - Date.now()) / (1000 * 60 * 60 * 24);
  return diff <= days;
}

export function formatQuantity(value: number): string {
  if (Number.isInteger(value)) return value.toString();
  return value.toFixed(1);
}

export function won(n: number | null | undefined): string {
  if (n === null || n === undefined) return "—";
  return `${Math.round(Number(n)).toLocaleString("ko-KR")}원`;
}

/** 5000 g → "5 kg", 1500 ml → "1.5 L" 보조 표기 */
export function bigUnitHint(qty: number, unit: string | null | undefined): string | null {
  if (qty < 1000) return null;
  if (unit === "g") return `${formatQuantity(qty / 1000)} kg`;
  if (unit === "ml") return `${formatQuantity(qty / 1000)} L`;
  return null;
}
