import { useState } from "react";
import { Text, View, type LayoutChangeEvent } from "react-native";
import Svg, { Line, Path, Text as SvgText, Circle } from "react-native-svg";
import { C } from "./theme";

export interface ChartPoint {
  date: string;
  price: number;
}

const MONTHLY = "#F97316";
const WEEKLY = "#8B5CF6";
const CURRENT = "#10B981";

/** recharts 대체 — react-native-svg 로 그린 30일 가격 라인 차트 */
export function PriceChart({
  history,
  monthly,
  weekly,
  current,
  height = 200,
}: {
  history: ChartPoint[];
  monthly: number;
  weekly: number;
  current: number;
  height?: number;
}) {
  const [width, setWidth] = useState(0);
  const onLayout = (e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width);

  const padL = 48;
  const padR = 8;
  const padT = 10;
  const padB = 22;

  const prices = history.map((p) => p.price);
  const refs = [monthly, weekly, current].filter((v) => v > 0);
  const minP = Math.min(...prices, ...refs);
  const maxP = Math.max(...prices, ...refs);
  const pad = (maxP - minP) * 0.15 || 1;
  const lo = minP - pad;
  const hi = maxP + pad;

  const w = Math.max(width - padL - padR, 1);
  const h = height - padT - padB;
  const x = (i: number) => padL + (history.length <= 1 ? w / 2 : (i / (history.length - 1)) * w);
  const y = (v: number) => padT + (1 - (v - lo) / (hi - lo)) * h;

  const path = history.map((p, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(p.price).toFixed(1)}`).join(" ");
  const ticks = [hi - pad * 0.5, (hi + lo) / 2, lo + pad * 0.5];
  const xLabels = history.length
    ? [0, Math.floor((history.length - 1) / 2), history.length - 1].filter((v, i, a) => a.indexOf(v) === i)
    : [];
  const last = history[history.length - 1];

  return (
    <View>
      <View onLayout={onLayout} style={{ height }}>
        {width > 0 && (
          <Svg width={width} height={height}>
            {ticks.map((t) => (
              <SvgText key={t} x={padL - 6} y={y(t) + 4} fontSize={10} fill={C.textMute} textAnchor="end">
                {Math.round(t).toLocaleString("ko-KR")}
              </SvgText>
            ))}
            {ticks.map((t) => (
              <Line key={`g${t}`} x1={padL} x2={padL + w} y1={y(t)} y2={y(t)} stroke="#F1F5F9" strokeWidth={1} />
            ))}
            {monthly > 0 && (
              <Line x1={padL} x2={padL + w} y1={y(monthly)} y2={y(monthly)} stroke={MONTHLY} strokeDasharray="6 4" strokeWidth={1.5} />
            )}
            {weekly > 0 && (
              <Line x1={padL} x2={padL + w} y1={y(weekly)} y2={y(weekly)} stroke={WEEKLY} strokeDasharray="6 4" strokeWidth={1.5} />
            )}
            {current > 0 && (
              <Line x1={padL} x2={padL + w} y1={y(current)} y2={y(current)} stroke={CURRENT} strokeWidth={1} opacity={0.6} />
            )}
            <Path d={path} stroke={C.primary} strokeWidth={2.5} fill="none" strokeLinejoin="round" strokeLinecap="round" />
            {last && <Circle cx={x(history.length - 1)} cy={y(last.price)} r={4} fill={C.primary} />}
            {xLabels.map((i) => (
              <SvgText
                key={`x${i}`}
                x={x(i)}
                y={height - 6}
                fontSize={10}
                fill={C.textSub}
                textAnchor={i === 0 ? "start" : i === history.length - 1 ? "end" : "middle"}
              >
                {history[i].date.slice(5)}
              </SvgText>
            ))}
          </Svg>
        )}
      </View>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 14, marginTop: 10 }}>
        <Legend color={MONTHLY} label="월 평균" value={monthly} dashed />
        <Legend color={WEEKLY} label="주 평균" value={weekly} dashed />
        <Legend color={CURRENT} label="현재" value={current} />
      </View>
    </View>
  );
}

function Legend({ color, label, value, dashed }: { color: string; label: string; value: number; dashed?: boolean }) {
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
      <View
        style={{
          width: 16,
          height: 0,
          borderTopWidth: 2,
          borderColor: color,
          borderStyle: dashed ? "dashed" : "solid",
        }}
      />
      <Text style={{ fontSize: 12, color: C.textSub }}>{label}</Text>
      <Text style={{ fontSize: 12, color: C.text, fontWeight: "600" }}>{value.toLocaleString("ko-KR")}원</Text>
    </View>
  );
}
