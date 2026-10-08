interface PriceTrendBadgeProps {
  current: number;
  average: number;
  label?: string;
}

export function PriceTrendBadge({
  current,
  average,
  label = "월 평균 대비",
}: PriceTrendBadgeProps) {
  if (!average || average === 0) return null;

  const diff = current - average;
  const diffPct = (diff / average) * 100;
  const absPct = Math.abs(diffPct).toFixed(1);

  const isEqual = Math.abs(diffPct) < 0.5;
  const isCheaper = diffPct < 0;

  const tone = isEqual
    ? "bg-[#f1f5f9] text-[#64748b] border-[#e2e8f0]"
    : isCheaper
      ? "bg-green-50 text-green-700 border-green-200"
      : "bg-red-50 text-red-700 border-red-200";

  const arrow = isEqual ? "=" : isCheaper ? "↓" : "↑";
  const text = isEqual ? label : `${arrow} ${absPct}% (${label})`;

  return (
    <span
      className={`inline-flex items-center text-xs font-medium px-2 py-1 rounded-md border ${tone}`}
    >
      {text}
    </span>
  );
}
