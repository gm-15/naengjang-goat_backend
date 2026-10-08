import { useMemo, useState } from "react";
import { motion } from "motion/react";
import { useNavigate } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { EmptyState } from "../components/common/EmptyState";
import { CardSkeleton } from "../components/common/LoadingState";
import { PriceTrendBadge } from "../components/common/PriceTrendBadge";
import { useLowestTop } from "../hooks/useLowestTop";
import type { LowestTopItem } from "../types/ingredient";

export default function LowestPricePage() {
  const navigate = useNavigate();
  const { data: items = [], isLoading, isError, refetch } = useLowestTop(20);
  const [searchQuery, setSearchQuery] = useState("");

  const filtered = useMemo(() => {
    if (!searchQuery.trim()) return items;
    const q = searchQuery.toLowerCase();
    return items.filter((item) => item.name.toLowerCase().includes(q));
  }, [items, searchQuery]);

  return (
    <AppShell variant="main">
      <PageHeader
        title="최저가 재료"
        description="공공 시세 + 온라인 최저가 비교"
        backTo="/main"
      />

      {/* Search */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="mb-6"
      >
        <div className="relative">
          <svg
            className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-[#94a3b8]"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <input
            type="text"
            placeholder="재료 검색..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-12 pr-4 py-3.5 rounded-xl bg-white border-2 border-[#e2e8f0] focus:border-[#0EA5E9] focus:outline-none transition-colors placeholder:text-[#94a3b8]"
          />
        </div>
      </motion.div>

      {/* List */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        {isLoading ? (
          <CardSkeleton count={5} heightClass="h-32" />
        ) : isError ? (
          <div className="text-center py-12">
            <p className="text-[#64748b] mb-4">
              재료 목록을 불러오지 못했습니다.
            </p>
            <button
              onClick={() => refetch()}
              className="px-5 py-2.5 rounded-xl border-2 border-[#0EA5E9] text-[#0EA5E9] font-medium hover:bg-[#F0F9FF] transition-colors"
            >
              다시 시도
            </button>
          </div>
        ) : filtered.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {filtered.map((item, index) => (
              <PriceCard
                key={item.ingredientId}
                item={item}
                index={index}
                onClick={() => navigate(`/lowest-price/${item.ingredientId}`)}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            title={
              searchQuery
                ? "검색 결과가 없습니다"
                : "등록된 재료가 아직 없습니다"
            }
            description={
              searchQuery
                ? "다른 검색어를 시도해 보세요."
                : "온보딩에서 매장 카테고리를 선택하면 재료가 자동 등록됩니다."
            }
          />
        )}
      </motion.div>
    </AppShell>
  );
}

function PriceCard({
  item,
  index,
  onClick,
}: {
  item: LowestTopItem;
  index: number;
  onClick: () => void;
}) {
  const hasPriceData = item.todayPrice !== null;
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.03 * index }}
      onClick={onClick}
      className="bg-white rounded-xl p-4 border-2 border-[#e2e8f0] hover:border-[#0EA5E9] hover:shadow-md transition-all cursor-pointer group relative"
    >
      <h3 className="font-semibold text-[#1e293b] mb-3 truncate">
        {item.name}
      </h3>

      {hasPriceData ? (
        <>
          <div className="flex items-baseline gap-1 mb-2">
            <span className="text-xs text-[#94a3b8]">오늘</span>
            <span className="text-xl font-semibold text-[#0EA5E9]">
              {item.todayPrice!.toLocaleString()}원
            </span>
            <span className="text-xs text-[#94a3b8]">/kg</span>
          </div>

          {item.monthAvg !== null && (
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xs text-[#94a3b8]">
                월 평균 {item.monthAvg.toLocaleString()}원
              </span>
              <PriceTrendBadge
                current={item.todayPrice!}
                average={item.monthAvg}
              />
            </div>
          )}
        </>
      ) : (
        <div className="space-y-1.5">
          <div className="text-sm text-[#94a3b8]">— 가격 수집 대기 중</div>
          <div className="text-xs text-[#cbd5e1]">
            KAMIS 일일 배치로 자동 갱신됩니다
          </div>
        </div>
      )}

      <svg
        className="absolute right-4 bottom-4 w-5 h-5 text-[#e2e8f0] group-hover:text-[#0EA5E9] group-hover:translate-x-1 transition-all"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 5l7 7-7 7"
        />
      </svg>
    </motion.div>
  );
}
