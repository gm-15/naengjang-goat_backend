import { useMemo } from "react";
import { motion } from "motion/react";
import { useNavigate } from "react-router";
import {
  thisMonthRange,
  usePurchaseSummary,
} from "../../hooks/usePurchaseOrders";

export function PurchaseSummaryWidget() {
  const navigate = useNavigate();
  const range = useMemo(() => thisMonthRange(), []);
  const { data, isLoading } = usePurchaseSummary(range.from, range.to);

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
      className="bg-white rounded-2xl p-5 border-2 border-[#e2e8f0] mb-6"
    >
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="text-base font-semibold text-[#1e293b] flex items-center gap-2">
            <span>📊</span>
            <span>이번 달 발주</span>
          </h3>
          <p className="text-xs text-[#94a3b8] mt-0.5">
            {range.from} ~ {range.to}
          </p>
        </div>
        <button
          onClick={() => navigate("/order?tab=history")}
          className="text-xs text-[#0EA5E9] hover:underline"
        >
          전체 보기 →
        </button>
      </div>

      {isLoading ? (
        <div className="h-20 bg-[#f1f5f9] rounded-lg animate-pulse" />
      ) : !data || data.totalCount === 0 ? (
        <div className="py-6 text-center text-sm text-[#94a3b8]">
          이번 달 발주 기록이 없습니다
        </div>
      ) : (
        <>
          <div className="flex items-baseline gap-3 mb-4">
            <span className="text-3xl font-bold text-[#0EA5E9]">
              {Number(data.totalAmount).toLocaleString()}
            </span>
            <span className="text-sm text-[#64748b]">원</span>
            <span className="text-xs text-[#94a3b8] ml-auto">
              총 {data.totalCount}건
            </span>
          </div>

          {data.byIngredient.length > 0 && (
            <div>
              <div className="text-xs text-[#64748b] font-medium mb-2">
                인기 재료 TOP 3
              </div>
              <div className="space-y-1.5">
                {data.byIngredient.slice(0, 3).map((row, i) => (
                  <div
                    key={row.ingredientName}
                    className="flex items-center justify-between text-sm"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <span className="w-5 h-5 rounded-full bg-[#F0F9FF] text-[#0EA5E9] text-xs font-bold flex items-center justify-center flex-shrink-0">
                        {i + 1}
                      </span>
                      <span className="text-[#1e293b] truncate">
                        {row.ingredientName}
                      </span>
                      <span className="text-xs text-[#94a3b8] flex-shrink-0">
                        {row.count}건
                      </span>
                    </div>
                    <span className="font-semibold text-[#0EA5E9] flex-shrink-0">
                      {Number(row.totalAmount).toLocaleString()}원
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </motion.div>
  );
}
