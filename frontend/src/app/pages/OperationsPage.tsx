import { useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { MenuRecipeEditor } from "../components/common/MenuRecipeEditor";
import {
  getDailyReport,
  listMenus,
  listPosMappings,
  savePosMapping,
  uploadPos,
} from "../api/workflow";
import { downloadBlob, fetchBlob } from "../api/client";
import {
  formatQuantity,
  invalidateOperations,
  todayKorean,
} from "../lib/workflow";
import type { DailyReport, UploadResult } from "../types/workflow";

const panel = "rounded-2xl border border-[#e2e8f0] bg-white p-5 sm:p-6";
const field =
  "w-full rounded-xl border border-[#cbd5e1] bg-white px-3 py-2.5 text-[#1e293b] focus:border-[#0EA5E9] focus:outline-none focus:ring-2 focus:ring-[#0EA5E9]/20";
const button =
  "rounded-xl bg-[#0EA5E9] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[#0284c7] disabled:cursor-not-allowed disabled:opacity-50";
const money = (value: number) =>
  `${Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 2 })}원`;
const time = (value: string | null | undefined) =>
  value ? `${value.replace("T", " ").slice(0, 19)} (한국 시간)` : "없음";
const errorMessage = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "처리하지 못했습니다. 다시 시도해주세요.";

export default function OperationsPage() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState("");
  const [uploadResult, setUploadResult] = useState<UploadResult | null>(null);
  const [businessDate, setBusinessDate] = useState(todayKorean);
  const [posCode, setPosCode] = useState("");
  const [menuId, setMenuId] = useState("");
  const [mappingMessage, setMappingMessage] = useState("");

  const menus = useQuery({ queryKey: ["menus"], queryFn: listMenus });
  const mappings = useQuery({
    queryKey: ["pos-menu-mappings"],
    queryFn: listPosMappings,
  });
  const report = useQuery({
    queryKey: ["daily-report", businessDate],
    queryFn: () => getDailyReport(businessDate),
    enabled: /^\d{4}-\d{2}-\d{2}$/.test(businessDate),
  });
  const upload = useMutation({
    mutationFn: uploadPos,
    onSuccess: (result) => {
      setUploadResult(result);
      setBusinessDate(result.businessDate);
      invalidateOperations(queryClient);
      setFile(null);
      if (fileInput.current) fileInput.current.value = "";
    },
  });
  const saveMapping = useMutation({
    mutationFn: ({ code, id }: { code: string; id: number }) =>
      savePosMapping(code, id),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ["pos-menu-mappings"] });
      const name =
        menus.data?.find((menu) => menu.menuId === result.menuId)?.name ??
        "메뉴";
      setMappingMessage(`${result.posCode} → ${name} 연결을 저장했습니다.`);
      setPosCode("");
      setMenuId("");
    },
  });
  const downloadTemplate = useMutation({
    mutationFn: () => fetchBlob("/pos/template"),
    onSuccess: (blob) => downloadBlob(blob, "냉장GOAT_POS_판매양식.xlsx"),
  });

  function chooseFile(nextFile: File | null) {
    upload.reset();
    setUploadResult(null);
    setFileError("");
    setFile(null);
    if (!nextFile) return;
    if (
      !nextFile.name.toLowerCase().endsWith(".xlsx") ||
      nextFile.size === 0 ||
      nextFile.size > 5_000_000
    ) {
      setFileError("내용이 있는 5MB 이하 .xlsx 파일을 선택해주세요.");
      if (fileInput.current) fileInput.current.value = "";
      return;
    }
    setFile(nextFile);
  }

  function submitMapping(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = posCode.trim();
    if (!code || !menuId) return;
    setMappingMessage("");
    saveMapping.mutate({ code, id: Number(menuId) });
  }

  return (
    <AppShell variant="main">
      <PageHeader
        title="영업 마감 · 리포트"
        description="POS 판매 내역을 반영하고 오늘의 판매·재료 소비 결과를 확인하세요."
        backTo="/main"
      />
      <div className="space-y-5">
        <MenuRecipeEditor />
        <section className={panel} aria-labelledby="pos-upload-title">
          <h2
            id="pos-upload-title"
            className="text-lg font-semibold text-[#1e293b]"
          >
            1. POS 판매 엑셀 업로드
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-[#64748b]">
            영업을 마친 뒤 POS에서 받은 판매 내역을 올려주세요. 업로드에
            성공하면 레시피에 따라 재료가 차감되고 발주 판단이 갱신됩니다.
          </p>
          <div className="mt-4 rounded-xl bg-[#F0F9FF] p-4 text-sm leading-relaxed text-[#475569]">
            <p>
              <strong>시트 이름:</strong> 판매내역
            </p>
            <p className="mt-1 break-words">
              <strong>첫 행 순서:</strong> 영업일 · 메뉴코드 · 메뉴명 · 판매수량
              · 판매금액
            </p>
            <p className="mt-2">
              한 파일에는 한 영업일의 메뉴별 합산 내역을 담아주세요. 날짜는
              YYYY-MM-DD, 판매수량은 양의 정수, 판매금액은 메뉴별 총
              판매금액입니다. 수식 대신 값으로 저장해주세요.
            </p>
            <p className="mt-2">
              메뉴코드를 쓰면 아래에서 매장 메뉴와 먼저 연결해주세요. 메뉴코드가
              비어 있으면 등록된 메뉴명과 정확히 같은 이름으로 연결합니다.
            </p>
          </div>
          <button
            type="button"
            data-testid="pos-template"
            disabled={downloadTemplate.isPending}
            onClick={() => downloadTemplate.mutate()}
            className="mt-3 rounded-xl border border-[#bae6fd] px-4 py-2.5 text-sm font-medium text-[#0369a1] disabled:opacity-50"
          >
            {downloadTemplate.isPending
              ? "양식 준비 중…"
              : "POS 판매 엑셀 양식 다운로드"}
          </button>
          {downloadTemplate.isError && (
            <p role="alert" className="mt-2 text-sm text-red-700">
              {errorMessage(downloadTemplate.error)}
            </p>
          )}
          <form
            className="mt-4 space-y-3"
            onSubmit={(event) => {
              event.preventDefault();
              if (file && !upload.isPending) upload.mutate(file);
            }}
          >
            <label
              htmlFor="pos-file"
              className="block text-sm font-medium text-[#334155]"
            >
              POS 판매 파일 (.xlsx · 최대 5MB)
            </label>
            <input
              id="pos-file"
              data-testid="pos-file"
              ref={fileInput}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              disabled={upload.isPending}
              onChange={(event) => chooseFile(event.target.files?.[0] ?? null)}
              className="block w-full text-sm text-[#64748b] file:mr-3 file:rounded-lg file:border-0 file:bg-[#E0F2FE] file:px-3 file:py-2 file:font-medium file:text-[#0369a1]"
            />
            {file && (
              <p className="break-all text-sm text-[#64748b]">
                {file.name} · {formatQuantity(file.size / 1_000_000)}MB
              </p>
            )}
            {(fileError || upload.isError) && (
              <p
                role="alert"
                className="rounded-lg bg-red-50 p-3 text-sm text-red-700"
              >
                {fileError || errorMessage(upload.error)}
                <br />
                파일 내용과 메뉴 연결을 확인한 뒤 다시 시도해주세요.
              </p>
            )}
            <button
              data-testid="pos-upload"
              type="submit"
              className={button}
              disabled={!file || upload.isPending}
            >
              {upload.isPending ? "판매 반영 중…" : "판매 반영하기"}
            </button>
          </form>
          {uploadResult && (
            <div
              role="status"
              className="mt-4 rounded-xl border border-[#bae6fd] bg-[#F0F9FF] p-4 text-sm text-[#075985]"
            >
              <p className="font-semibold">
                {uploadResult.duplicate
                  ? "이미 반영한 파일입니다"
                  : `${uploadResult.businessDate} 판매 반영 완료`}
              </p>
              <p className="mt-1">
                {uploadResult.duplicate
                  ? "재고 차감과 알림은 중복 처리하지 않았습니다. 기존 반영 결과를 아래에서 확인하세요."
                  : `판매 ${formatQuantity(uploadResult.salesQuantity)}개 · ${money(uploadResult.salesAmount)}를 반영했습니다. 아래 리포트에서 재료 소비량과 잔여 재고를 확인하세요.`}
              </p>
            </div>
          )}
          <p className="mt-3 text-xs leading-relaxed text-[#64748b]">
            같은 파일은 한 번만 반영됩니다. 이미 반영한 영업일의 다른 파일은
            추가로 반영할 수 없습니다. 메뉴 연결 또는 재고가 부족해 실패하면
            판매 내역 전체가 반영되지 않습니다.
          </p>
        </section>

        <section className={panel} aria-labelledby="pos-mapping-title">
          <h2
            id="pos-mapping-title"
            className="text-lg font-semibold text-[#1e293b]"
          >
            POS 메뉴 연결
          </h2>
          <p className="mt-2 text-sm text-[#64748b]">
            엑셀의 메뉴코드를 매장 메뉴에 연결합니다. 이미 연결한 코드를
            저장하면 연결할 메뉴가 변경됩니다.
          </p>
          {menus.isLoading ? (
            <p className="mt-4 text-sm text-[#64748b]">
              매장 메뉴를 불러오는 중…
            </p>
          ) : menus.isError ? (
            <div role="alert" className="mt-4 text-sm text-red-700">
              {errorMessage(menus.error)}{" "}
              <button
                type="button"
                className="underline"
                onClick={() => void menus.refetch()}
              >
                다시 불러오기
              </button>
            </div>
          ) : !menus.data?.length ? (
            <div className="mt-4 rounded-xl bg-[#F0F9FF] p-4 text-sm text-[#475569]">
              등록된 매장 메뉴가 없습니다.{" "}
              <Link
                to="/onboard"
                className="font-semibold text-[#0284c7] underline"
              >
                매장 설정
              </Link>
              에서 메뉴·레시피를 준비한 뒤 판매 내역을 올려주세요.
            </div>
          ) : (
            <form
              onSubmit={submitMapping}
              className="mt-4 grid gap-3 sm:grid-cols-[1fr_1.5fr_auto] sm:items-end"
            >
              <label className="block text-sm font-medium text-[#334155]">
                POS 메뉴코드
                <input
                  value={posCode}
                  maxLength={100}
                  required
                  disabled={saveMapping.isPending}
                  onChange={(event) => {
                    setPosCode(event.target.value);
                    setMappingMessage("");
                    saveMapping.reset();
                  }}
                  placeholder="예: M001"
                  className={`${field} mt-1`}
                />
              </label>
              <label className="block text-sm font-medium text-[#334155]">
                연결할 매장 메뉴
                <select
                  value={menuId}
                  required
                  disabled={saveMapping.isPending}
                  onChange={(event) => {
                    setMenuId(event.target.value);
                    setMappingMessage("");
                    saveMapping.reset();
                  }}
                  className={`${field} mt-1`}
                >
                  <option value="">메뉴 선택</option>
                  {menus.data.map((menu) => (
                    <option key={menu.menuId} value={menu.menuId}>
                      {menu.name}
                    </option>
                  ))}
                </select>
              </label>
              <button
                className={button}
                type="submit"
                disabled={!posCode.trim() || !menuId || saveMapping.isPending}
              >
                {saveMapping.isPending ? "저장 중…" : "연결 저장"}
              </button>
            </form>
          )}
          {saveMapping.isError && (
            <p role="alert" className="mt-3 text-sm text-red-700">
              {errorMessage(saveMapping.error)}
            </p>
          )}
          {mappingMessage && (
            <p role="status" className="mt-3 text-sm text-[#0369a1]">
              {mappingMessage}
            </p>
          )}
          {mappings.isLoading ? (
            <p className="mt-4 text-sm text-[#64748b]">
              메뉴 연결을 불러오는 중…
            </p>
          ) : mappings.isError ? (
            <div role="alert" className="mt-4 text-sm text-red-700">
              {errorMessage(mappings.error)}{" "}
              <button
                type="button"
                className="underline"
                onClick={() => void mappings.refetch()}
              >
                다시 불러오기
              </button>
            </div>
          ) : mappings.data?.length ? (
            <ul className="mt-4 divide-y divide-[#e2e8f0] border-t border-[#e2e8f0] text-sm">
              {mappings.data.map((mapping) => (
                <li
                  key={mapping.id}
                  className="flex flex-wrap items-center justify-between gap-2 py-3"
                >
                  <span className="break-all font-medium text-[#475569]">
                    {mapping.posCode}
                  </span>
                  <span className="text-[#0369a1]">
                    {menus.data?.find((menu) => menu.menuId === mapping.menuId)
                      ?.name ?? `메뉴 #${mapping.menuId}`}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-4 text-sm text-[#94a3b8]">
              저장한 POS 메뉴 연결이 없습니다.
            </p>
          )}
        </section>

        <section className={panel} aria-labelledby="daily-report-title">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <h2
              id="daily-report-title"
              className="text-lg font-semibold text-[#1e293b]"
            >
              2. 일일 운영 리포트
            </h2>
            <label className="text-sm font-medium text-[#334155]">
              리포트 영업일
              <input
                aria-label="리포트 영업일"
                data-testid="report-date"
                type="date"
                value={businessDate}
                max={todayKorean()}
                onChange={(event) => setBusinessDate(event.target.value)}
                className={`${field} mt-1`}
              />
            </label>
          </div>
          {!businessDate ? (
            <p className="mt-4 text-sm text-[#64748b]">
              확인할 영업일을 선택해주세요.
            </p>
          ) : report.isLoading ? (
            <p className="mt-5 text-sm text-[#64748b]">리포트를 불러오는 중…</p>
          ) : report.isError ? (
            <div
              role="alert"
              className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700"
            >
              {errorMessage(report.error)}{" "}
              <button
                type="button"
                className="underline"
                onClick={() => void report.refetch()}
              >
                다시 불러오기
              </button>
            </div>
          ) : (
            report.data && <ReportContents report={report.data} />
          )}
        </section>
      </div>
    </AppShell>
  );
}

function ReportContents({ report }: { report: DailyReport }) {
  return (
    <div className="mt-4 space-y-5">
      {report.uploaded ? (
        <p className="text-sm text-[#0369a1]">
          {report.businessDate} 판매 반영 시각: {time(report.reflectedAt)}
        </p>
      ) : (
        <p className="rounded-xl bg-amber-50 p-4 text-sm leading-relaxed text-amber-800">
          {report.businessDate} 판매 내역이 아직 반영되지 않았습니다. 판매가
          없었던 것으로 판단하지 않고, 업로드 후 결과를 표시합니다.
        </p>
      )}
      {report.uploaded && (
        <>
          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-xl bg-[#F0F9FF] p-4">
              <p className="text-sm text-[#64748b]">판매금액</p>
              <p className="mt-1 break-all text-xl font-bold text-[#0284c7]">
                {money(report.salesAmount)}
              </p>
            </div>
            <div className="rounded-xl bg-[#F0F9FF] p-4">
              <p className="text-sm text-[#64748b]">판매 메뉴 수량</p>
              <p className="mt-1 text-xl font-bold text-[#0284c7]">
                {formatQuantity(report.salesQuantity)}개
              </p>
            </div>
          </div>
          <div>
            <h3 className="mb-2 font-semibold text-[#334155]">메뉴별 판매량</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <caption className="sr-only">
                  선택한 영업일의 메뉴별 판매 수량과 판매금액
                </caption>
                <thead>
                  <tr className="border-b border-[#e2e8f0] text-left text-[#64748b]">
                    <th scope="col" className="py-2 pr-3 font-medium">
                      메뉴
                    </th>
                    <th
                      scope="col"
                      className="whitespace-nowrap py-2 pr-3 text-right font-medium"
                    >
                      수량
                    </th>
                    <th
                      scope="col"
                      className="whitespace-nowrap py-2 text-right font-medium"
                    >
                      판매금액
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {report.menuSales.map((sale) => (
                    <tr
                      key={sale.menuId}
                      className="border-b border-[#f1f5f9] text-[#334155]"
                    >
                      <td className="py-3 pr-3">{sale.menuName}</td>
                      <td className="whitespace-nowrap py-3 pr-3 text-right">
                        {formatQuantity(sale.quantity)}개
                      </td>
                      <td className="whitespace-nowrap py-3 text-right">
                        {money(sale.salesAmount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div>
            <h3 className="mb-2 font-semibold text-[#334155]">
              판매로 소비한 재료
            </h3>
            <ul className="divide-y divide-[#e2e8f0] text-sm">
              {report.ingredientConsumption.map((item) => (
                <li
                  key={item.ingredientId}
                  className="flex items-center justify-between gap-3 py-3"
                >
                  <span className="text-[#475569]">{item.ingredientName}</span>
                  <span className="whitespace-nowrap font-medium text-[#334155]">
                    {formatQuantity(item.quantity)} {item.baseUnit}
                  </span>
                </li>
              ))}
            </ul>
            {!report.ingredientConsumption.length && (
              <p className="text-sm text-[#64748b]">
                반영된 재료 소비 내역이 없습니다.
              </p>
            )}
          </div>
        </>
      )}
      <div className="border-t border-[#e2e8f0] pt-4">
        <h3 className="font-semibold text-[#334155]">현재 잔여 재고</h3>
        <p className="mt-1 text-xs leading-relaxed text-[#64748b]">
          조회 시각: {time(report.inventoryAsOf)}
          <br />
          판매·소비량은 선택한 영업일의 기록이며, 잔여 재고는 지금 조회한
          수량입니다. 입고·추가 판매가 반영된 현재 재고로 표시합니다.
        </p>
        {report.lastUploadedBusinessDate && (
          <p className="mt-2 text-xs text-[#64748b]">
            마지막 판매 반영 영업일: {report.lastUploadedBusinessDate}
          </p>
        )}
        {report.currentInventory.length ? (
          <ul className="mt-2 divide-y divide-[#e2e8f0] text-sm">
            {report.currentInventory.map((item) => (
              <li
                key={item.ingredientId}
                className="flex items-center justify-between gap-3 py-3"
              >
                <span className="text-[#475569]">{item.ingredientName}</span>
                <span className="whitespace-nowrap font-semibold text-[#0284c7]">
                  {formatQuantity(item.quantity)} {item.baseUnit}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-3 text-sm text-[#64748b]">
            조회할 재료가 없습니다. 매장 재료와 초기 재고를 등록해주세요.
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-3 border-t border-[#e2e8f0] pt-4 text-sm">
        <Link
          to="/order"
          className="rounded-xl bg-[#E0F2FE] px-4 py-2.5 font-semibold text-[#0369a1]"
        >
          내일 필요한 발주 확인
        </Link>
        <Link
          to="/inventory"
          className="rounded-xl border border-[#cbd5e1] px-4 py-2.5 font-medium text-[#475569]"
        >
          재고 관리
        </Link>
      </div>
    </div>
  );
}
