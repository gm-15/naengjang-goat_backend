import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { getClosingNotification } from "../api/workflow";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { RecommendationsPanel } from "../components/common/RecommendationsPanel";
export default function NotificationDetailPage() {
  const { id } = useParams();
  const notificationId = Number(id);
  const validId = Number.isSafeInteger(notificationId) && notificationId > 0;
  const query = useQuery({
    queryKey: ["closing-notification", notificationId],
    queryFn: () => getClosingNotification(notificationId),
    enabled: validId,
  });
  return (
    <AppShell variant="main">
      <PageHeader title="알림 상세" backTo="/order" />
      {!validId ? (
        <p role="alert">알림 주소를 확인해주세요.</p>
      ) : query.isLoading ? (
        <p>알림을 불러오고 있습니다…</p>
      ) : query.error ? (
        <div role="alert" className="p-4 bg-red-50 text-red-700 rounded-xl">
          {query.error.message}
          <button
            onClick={() => query.refetch()}
            className="block underline mt-2"
          >
            다시 시도
          </button>
        </div>
      ) : (
        query.data && (
          <>
            <div className="mb-5 p-4 rounded-xl bg-sky-50 space-y-2">
              <p className="text-sm text-sky-700">
                {query.data.type === "UPLOAD"
                  ? "판매 반영 후 알림"
                  : "영업 시작 3시간 전 알림"}
              </p>
              <h2 className="text-lg font-semibold">{query.data.title}</h2>
              <p className="text-sm text-slate-600">{query.data.body}</p>
              <p className="text-xs text-slate-500">
                생성 시각 {query.data.createdAt.replace("T", " ")} · 이 화면은
                당시 판단을 보존합니다.
              </p>
            </div>
            <RecommendationsPanel data={query.data.recommendations} />
            <Link
              to="/order"
              className="inline-block mt-5 rounded-xl bg-sky-500 text-white px-4 py-3"
            >
              지금 기준으로 발주 확인
            </Link>
          </>
        )
      )}
    </AppShell>
  );
}
