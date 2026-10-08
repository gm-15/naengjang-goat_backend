import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
export interface ChartPoint {
  date: string;       // "YYYY-MM-DD"
  price: number;
}

interface PriceChartProps {
  history: ChartPoint[];
  monthly: number;
  weekly: number;
  current: number;
  unit?: string;
  heightClassName?: string;
}

const MONTHLY_COLOR = "#f97316";
const WEEKLY_COLOR = "#8b5cf6";
const CURRENT_COLOR = "#10b981";

export function PriceChart({
  history,
  monthly,
  weekly,
  current,
  unit = "원",
  heightClassName = "h-56 lg:h-72",
}: PriceChartProps) {
  const data = history.map((p) => ({
    date: p.date.slice(5),
    price: p.price,
  }));

  const prices = history.map((p) => p.price);
  const minP = Math.min(...prices, monthly, weekly, current);
  const maxP = Math.max(...prices, monthly, weekly, current);
  const pad = Math.round((maxP - minP) * 0.15) || 1;

  return (
    <div className="w-full">
      <div className={`w-full ${heightClassName}`}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 10, right: 16, bottom: 0, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 11, fill: "#64748b" }}
              interval="preserveStartEnd"
              minTickGap={24}
            />
            <YAxis
              domain={[minP - pad, maxP + pad]}
              tick={{ fontSize: 11, fill: "#64748b" }}
              tickFormatter={(v) => v.toLocaleString()}
              width={60}
            />
            <Tooltip
              contentStyle={{
                borderRadius: 12,
                border: "2px solid #e2e8f0",
                fontSize: 12,
              }}
              formatter={(value: number) => [
                `${value.toLocaleString()}${unit}`,
                "가격",
              ]}
              labelFormatter={(label) => `${label}`}
            />
            <ReferenceLine
              y={monthly}
              stroke={MONTHLY_COLOR}
              strokeDasharray="6 4"
              label={{
                value: `월평균 ${monthly.toLocaleString()}`,
                position: "insideTopRight",
                fill: MONTHLY_COLOR,
                fontSize: 11,
              }}
            />
            <ReferenceLine
              y={weekly}
              stroke={WEEKLY_COLOR}
              strokeDasharray="6 4"
              label={{
                value: `주평균 ${weekly.toLocaleString()}`,
                position: "insideBottomRight",
                fill: WEEKLY_COLOR,
                fontSize: 11,
              }}
            />
            <ReferenceLine
              y={current}
              stroke={CURRENT_COLOR}
              strokeWidth={1.5}
            />
            <Line
              type="monotone"
              dataKey="price"
              stroke="#0EA5E9"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 5, stroke: "#0EA5E9", strokeWidth: 2, fill: "white" }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="flex flex-wrap gap-x-6 gap-y-2 mt-4 text-xs">
        <LegendItem color={MONTHLY_COLOR} label="월 평균" value={monthly} unit={unit} dashed />
        <LegendItem color={WEEKLY_COLOR} label="주 평균" value={weekly} unit={unit} dashed />
        <LegendItem color={CURRENT_COLOR} label="현재" value={current} unit={unit} />
      </div>
    </div>
  );
}

function LegendItem({
  color,
  label,
  value,
  unit,
  dashed = false,
}: {
  color: string;
  label: string;
  value: number;
  unit: string;
  dashed?: boolean;
}) {
  return (
    <div className="flex items-center gap-2">
      <span
        className="inline-block w-5 h-0.5"
        style={{
          backgroundColor: dashed ? "transparent" : color,
          borderTop: dashed ? `2px dashed ${color}` : undefined,
        }}
      />
      <span className="text-[#64748b]">{label}</span>
      <span className="font-semibold text-[#1e293b]">
        {value.toLocaleString()}
        {unit}
      </span>
    </div>
  );
}
