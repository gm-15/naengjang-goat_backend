import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { getRecommendations } from "../../api/workflow";
import { formatQuantity, todayKorean } from "../../lib/workflow";
import type { Recommendations } from "../../types/workflow";

export function RecommendationsPanel({
  data: snapshot,
}: {
  data?: Recommendations;
}) {
  const query = useQuery({
    queryKey: ["closing-recommendations", todayKorean()],
    queryFn: () => getRecommendations(todayKorean()),
    enabled: !snapshot,
  });
  const data = snapshot ?? query.data;
  if (!data && query.isPending)
    return (
      <p className="p-5 text-slate-500">재고와 가격을 확인하고 있습니다…</p>
    );
  if (!data)
    return (
      <div role="alert" className="p-4 bg-red-50 text-red-700 rounded-xl">
        {query.error instanceof Error
          ? query.error.message
          : "발주 판단을 불러오지 못했습니다."}
        <button
          onClick={() => query.refetch()}
          className="block mt-2 underline"
        >
          다시 시도
        </button>
      </div>
    );
  const known = data.items.filter((item) => item.recommendedQuantity != null);
  const attention = data.items.filter(
    (item) => item.stockAlert || item.buySignal,
  );
  return (
    <div className="space-y-4">
      <div className="bg-white p-4 rounded-xl border border-slate-200 text-sm text-slate-600 space-y-1">
        <p>
          판단일 {data.businessDate} ·{" "}
          {snapshot ? "알림 생성 당시" : "현재 가용 재고 기준"}
        </p>
        <p>
          최근 판매 반영: {data.lastUploadedBusinessDate ?? "판매 자료 없음"}
          {data.lastReflectedAt
            ? ` (${data.lastReflectedAt.replace("T", " ")})`
            : ""}
        </p>
        <p className="text-xs">
          {data.generatedAt.replace("T", " ")}에 계산 · 유통기한이 지난 재고는
          발주 판단에서 제외됩니다.
        </p>
      </div>
      {!data.settingsConfigured && (
        <div className="p-4 rounded-xl bg-amber-50 text-amber-800 text-sm">
          영업 시간과 발주 요일 설정이 필요합니다. 설정 전 수량은 기본 7일 기준
          참고값입니다.{" "}
          <Link to="/settings" className="underline">
            매장 설정
          </Link>
        </div>
      )}
      {!data.lastUploadedBusinessDate && (
        <div className="p-4 rounded-xl bg-amber-50 text-amber-800 text-sm">
          판매 데이터가 없어 재고 소진을 예측할 수 없습니다.{" "}
          <Link to="/operations" className="underline">
            POS 판매 자료 업로드
          </Link>
        </div>
      )}
      {data.items.length === 0 ? (
        <p className="p-6 text-center text-slate-500">
          등록된 재료가 없습니다.{" "}
          <Link to="/onboard" className="text-sky-600 underline">
            메뉴와 레시피 등록
          </Link>
        </p>
      ) : (
        <>
          <p className="text-sm text-slate-600">
            {attention.length > 0
              ? `확인할 재료 ${attention.length}개`
              : known.length === data.items.length && data.settingsConfigured
                ? "현재 판단에서 부족하거나 가격 신호가 있는 재료가 없습니다."
                : "판단에 필요한 정보가 부족한 재료가 있습니다. 아래 사유를 확인해주세요."}
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {[...data.items]
              .sort(
                (a, b) =>
                  Number(b.stockAlert || b.buySignal) -
                  Number(a.stockAlert || a.buySignal),
              )
              .map((item) => (
                <div
                  key={item.ingredientId}
                  className={`p-4 bg-white rounded-xl border-2 ${item.stockAlert ? "border-amber-300" : "border-slate-200"}`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="font-semibold text-slate-800">
                      {item.ingredientName}
                    </h3>
                    <div className="flex gap-1">
                      {item.stockAlert && (
                        <span className="text-xs rounded px-2 py-1 bg-amber-50 text-amber-800">
                          재고 부족 예상
                        </span>
                      )}
                      {item.buySignal && (
                        <span className="text-xs rounded px-2 py-1 bg-sky-50 text-sky-700">
                          가격 유리
                        </span>
                      )}
                    </div>
                  </div>
                  <dl className="mt-3 text-sm space-y-1 text-slate-600">
                    <div>
                      가용 재고 {formatQuantity(item.currentStock)}{" "}
                      {item.baseUnit}
                    </div>
                    <div>
                      일평균 소비 {formatQuantity(item.dailyAvgSales)}{" "}
                      {item.baseUnit}
                    </div>
                    <div>
                      권장 구매량:{" "}
                      {item.recommendedQuantity == null
                        ? "판단 보류"
                        : `${formatQuantity(item.recommendedQuantity)} ${item.baseUnit}${data.settingsConfigured ? "" : " (설정 전 참고값)"}`}
                    </div>
                    <div>
                      예상 소진일: {item.estimatedDepletionDate ?? "판단 보류"}
                    </div>
                  </dl>
                  <p className="mt-3 text-sm text-slate-700">{item.reason}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    가격: {item.priceReason || "가격 데이터 없음"} ·{" "}
                    {item.priceDataCoverage}/30일 자료
                  </p>
                  <Link
                    to={`/lowest-price/${item.ingredientId}`}
                    className="mt-3 inline-block text-sky-600 text-sm font-medium"
                  >
                    가격 비교 · 구매 기록 →
                  </Link>
                </div>
              ))}
          </div>
        </>
      )}
    </div>
  );
}
