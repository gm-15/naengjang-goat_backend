import { Text, View } from "react-native";
import { C } from "./theme";

export function PriceTrendBadge({
  current,
  average,
  label = "월 평균 대비",
}: {
  current: number;
  average: number;
  label?: string;
}) {
  if (!average) return null;
  const diffPct = ((current - average) / average) * 100;
  const isEqual = Math.abs(diffPct) < 0.5;
  const isCheaper = diffPct < 0;
  const tone = isEqual
    ? { bg: "#F1F5F9", fg: C.textSub, bd: C.border }
    : isCheaper
      ? { bg: "#F0FDF4", fg: "#15803D", bd: "#BBF7D0" }
      : { bg: C.dangerSoft, fg: "#B91C1C", bd: "#FECACA" };
  const arrow = isEqual ? "=" : isCheaper ? "↓" : "↑";
  const text = isEqual ? label : `${arrow} ${Math.abs(diffPct).toFixed(1)}% (${label})`;
  return (
    <View style={{ backgroundColor: tone.bg, borderColor: tone.bd, borderWidth: 1, borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3 }}>
      <Text style={{ fontSize: 12, color: tone.fg, fontWeight: "500" }}>{text}</Text>
    </View>
  );
}
