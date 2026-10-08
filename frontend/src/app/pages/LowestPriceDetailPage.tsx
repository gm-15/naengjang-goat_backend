import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { useNavigate, useParams } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { Spinner } from "../components/common/LoadingState";
import { usePriceDetail } from "../hooks/usePriceDetail";
import {
  PurchaseRecordDialog,
  type VisitedSource,
} from "../components/common/PurchaseRecordDialog";
import type { OnlinePrice, PriceDetail } from "../types/ingredient";

export default function LowestPriceDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, refetch } = usePriceDetail(Number(id));
  const [pending, setPending] = useState<VisitedSource | null>(null);
  const [recordSource, setRecordSource] = useState<VisitedSource | null>(null);
  useEffect(() => {
    const returned = () => {
      if (!document.hidden && pending) {
        setRecordSource(pending);
        setPending(null);
      }
    };
    document.addEventListener("visibilitychange", returned);
    window.addEventListener("focus", returned);
    return () => {
      document.removeEventListener("visibilitychange", returned);
      window.removeEventListener("focus", returned);
    };
  }, [pending]);
  const visit = (source: VisitedSource, url: string) => {
    setPending({ ...source, sourceUrl: url });
    window.open(url, "_blank", "noopener,noreferrer");
  };
  if (isLoading)
    return (
      <AppShell variant="main">
        <PageHeader title="가격 조회" backTo="/lowest-price" />
        <Spinner />
      </AppShell>
    );
  if (isError || !data)
    return (
      <AppShell variant="main">
        <PageHeader title="가격 조회" backTo="/lowest-price" />
        <p role="alert">재료 정보를 불러오지 못했습니다.</p>
        <button onClick={() => refetch()} className="text-sky-600 p-3">
          다시 시도
        </button>
      </AppShell>
    );
  return (
    <AppShell variant="main">
      <PageHeader title={data.name} backTo="/lowest-price" />
      <div className="mb-5 p-4 bg-sky-50 border border-sky-100 rounded-xl space-y-3">
        <p className="text-sm text-slate-600">
          외부 사이트에서 구매 후 이곳에 돌아와 실제 주문 내용을 기록하세요.
        </p>
        <button
          data-testid="purchase-open"
          onClick={() =>
            setRecordSource(
              pending ?? {
                source: "MANUAL",
                sourceLabel: "직접 입력",
                productName: data.name,
                price: 0,
              },
            )
          }
          className="px-4 py-2.5 bg-sky-500 text-white rounded-xl"
        >
          구매 기록 추가
        </button>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-4">
          <KamisCard data={data} />
          <PriceInfoCard hasOnline={data.onlinePrices.length > 0} />
        </div>
        <div>
          {data.onlinePrices.length > 0 ? (
            <OnlinePricesList prices={data.onlinePrices} onVisit={visit} />
          ) : (
            <SearchLinksFallback data={data} onVisit={visit} />
          )}
        </div>
      </div>
      {recordSource && (
        <PurchaseRecordDialog
          key={`${recordSource.source}-${recordSource.sourceUrl ?? ""}`}
          data={data}
          source={recordSource}
          onClose={() => {
            setRecordSource(null);
            setPending(null);
          }}
          onSaved={() => navigate("/order?tab=history&delivery=WAITING")}
        />
      )}
    </AppShell>
  );
}

function KamisCard({ data }: { data: PriceDetail }) {
  const k = data.kamis;
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
      className="p-5 rounded-2xl bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] text-white"
    >
      <div className="flex items-center gap-2 mb-3">
        <svg
          className="w-5 h-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
          />
        </svg>
        <span className="text-sm font-medium">공공 시세</span>
      </div>

      {k && k.currentPricePerKg !== null ? (
        <>
          <div className="flex items-baseline gap-2 mb-3">
            <span className="text-4xl font-bold">
              {k.currentPricePerKg.toLocaleString()}원
            </span>
            <span className="text-sm opacity-90">/ kg</span>
          </div>
          <div className="grid grid-cols-2 gap-3 text-xs">
            <div className="bg-white/10 rounded-lg px-3 py-2">
              <div className="opacity-80">주 평균</div>
              <div className="font-semibold text-sm mt-0.5">
                {k.weekAvg !== null ? `${k.weekAvg.toLocaleString()}원` : "—"}
              </div>
            </div>
            <div className="bg-white/10 rounded-lg px-3 py-2">
              <div className="opacity-80">월 평균</div>
              <div className="font-semibold text-sm mt-0.5">
                {k.monthAvg !== null ? `${k.monthAvg.toLocaleString()}원` : "—"}
              </div>
            </div>
          </div>
          {k.priceDate && (
            <div className="text-xs opacity-80 mt-3">기준일: {k.priceDate}</div>
          )}
        </>
      ) : (
        <div>
          <div className="text-2xl font-semibold mb-1">데이터 수집 중</div>
          <div className="text-xs opacity-90">
            아직 비교할 공공 가격 자료가 없습니다.
          </div>
        </div>
      )}
    </motion.div>
  );
}

function PriceInfoCard({ hasOnline }: { hasOnline: boolean }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.4 }}
      className="p-4 rounded-xl bg-white border-2 border-[#e2e8f0]"
    >
      <div className="flex items-start gap-3">
        <svg
          className="w-5 h-5 text-[#0EA5E9] mt-0.5 flex-shrink-0"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <div className="flex-1">
          <h3 className="font-semibold text-[#1e293b] mb-2">가격 비교 안내</h3>
          <ul className="space-y-1.5 text-sm text-[#64748b]">
            <li>• 공공 시세: KAMIS·EKAPE 수집 자료</li>
            <li>
              • 온라인 가격:{" "}
              {hasOnline
                ? "네이버 쇼핑·식자재왕 수집 가격"
                : "수집된 항목 없음 — 검색 페이지 링크로 대체"}
            </li>
            <li>• 가격은 수시로 변동될 수 있습니다</li>
          </ul>
        </div>
      </div>
    </motion.div>
  );
}

function OnlinePricesList({
  prices,
  onVisit,
}: {
  prices: OnlinePrice[];
  onVisit: (v: VisitedSource, url: string) => void;
}) {
  return (
    <>
      <h2 className="text-lg font-semibold text-[#1e293b] mb-3">
        온라인 최저가
      </h2>
      <div className="space-y-3">
        {prices.map((p, index) => (
          <motion.div
            key={`${p.source}-${index}`}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.05 * index }}
            className={`bg-white rounded-xl p-4 border-2 transition-all ${
              p.isLowest
                ? "border-[#0EA5E9] shadow-lg shadow-[#0EA5E9]/10"
                : "border-[#e2e8f0]"
            }`}
          >
            <div className="flex items-start justify-between mb-3 gap-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-[#F0F9FF] to-white flex items-center justify-center border-2 border-[#e2e8f0] flex-shrink-0">
                  <span className="text-base">🛒</span>
                </div>
                <div className="min-w-0">
                  <div className="font-semibold text-[#1e293b] truncate">
                    {p.sourceLabel}
                  </div>
                  <div className="text-xs text-[#94a3b8] truncate">
                    {p.productName}
                  </div>
                  {p.isLowest && (
                    <span className="inline-block mt-1 text-xs px-2 py-0.5 rounded-md bg-gradient-to-r from-[#0EA5E9] to-[#38BDF8] text-white">
                      최저가
                    </span>
                  )}
                </div>
              </div>
              <div className="text-right flex-shrink-0">
                <div className="text-xl font-bold text-[#0EA5E9]">
                  {p.price.toLocaleString()}원
                </div>
                {p.unitPricePerKg !== null && (
                  <div className="text-xs text-[#94a3b8]">
                    kg당 {p.unitPricePerKg.toLocaleString()}원
                  </div>
                )}
              </div>
            </div>

            <button
              data-testid={`online-purchase-${index}`}
              onClick={() =>
                onVisit(
                  {
                    source: p.source,
                    sourceLabel: p.sourceLabel,
                    productName: p.productName,
                    price: p.price,
                    weightGrams: p.weightGrams,
                  },
                  p.productUrl,
                )
              }
              className={`w-full py-2.5 rounded-lg font-medium transition-all flex items-center justify-center gap-2 ${
                p.isLowest
                  ? "bg-gradient-to-r from-[#0EA5E9] to-[#38BDF8] text-white hover:shadow-lg"
                  : "bg-[#F0F9FF] text-[#0EA5E9] hover:bg-[#0EA5E9] hover:text-white"
              }`}
            >
              <span>구매하러 가기</span>
              <svg
                className="w-4 h-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                />
              </svg>
            </button>
          </motion.div>
        ))}
      </div>
    </>
  );
}

function SearchLinksFallback({
  data,
  onVisit,
}: {
  data: PriceDetail;
  onVisit: (v: VisitedSource, url: string) => void;
}) {
  const labelMap: Record<string, string> = {
    NAVER_SEARCH: "네이버 쇼핑",
    SIKJAJAEWANG_SEARCH: "식자재왕",
  };
  return (
    <>
      <h2 className="text-lg font-semibold text-[#1e293b] mb-1">외부 검색</h2>
      <p className="text-sm text-[#64748b] mb-3">
        수집된 온라인 가격이 없습니다. 아래 사이트에서 직접 검색해 보세요.
      </p>
      <div className="space-y-3">
        {data.externalSearchLinks.map((link, index) => {
          const label = labelMap[link.source] ?? link.source;
          return (
            <motion.button
              key={link.source}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.05 * index }}
              onClick={() =>
                onVisit(
                  {
                    source: link.source,
                    sourceLabel: label,
                    productName: data.name,
                    price: 0,
                  },
                  link.url,
                )
              }
              className="w-full bg-white rounded-xl p-4 border-2 border-[#e2e8f0] hover:border-[#0EA5E9] transition-all text-left flex items-center justify-between group"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-[#F0F9FF] to-white flex items-center justify-center border-2 border-[#e2e8f0]">
                  <span className="text-base">🔍</span>
                </div>
                <div>
                  <div className="font-semibold text-[#1e293b]">{label}</div>
                  <div className="text-xs text-[#94a3b8]">
                    "{data.name}" 검색 페이지로 이동
                  </div>
                </div>
              </div>
              <svg
                className="w-5 h-5 text-[#94a3b8] group-hover:text-[#0EA5E9] group-hover:translate-x-1 transition-all"
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
            </motion.button>
          );
        })}
      </div>

      <div className="mt-4 p-3 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800">
        검색 후 돌아오면 실제 구매 내용을 기록할 수 있습니다. 창이 열리지 않으면
        위의 구매 기록 추가 버튼을 눌러주세요.
      </div>
    </>
  );
}
