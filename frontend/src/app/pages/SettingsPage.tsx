import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { useNavigate, useSearchParams } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { PageHeader } from "../components/common/PageHeader";
import { GradientButton } from "../components/common/GradientButton";
import { Spinner } from "../components/common/LoadingState";
import {
  useStoreSettings,
  useUpdateStoreSettings,
} from "../hooks/useStoreSettings";
import { DAY_OF_WEEK_LABEL, DAY_OF_WEEK_ORDER } from "../types/settings";
import type { DayOfWeek } from "../types/settings";

export default function SettingsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const isInitial = searchParams.get("initial") === "1";

  const { data: settings, isLoading } = useStoreSettings();
  const updateMutation = useUpdateStoreSettings();

  const [openTime, setOpenTime] = useState("11:00");
  const [closeTime, setCloseTime] = useState("22:00");
  const [orderDay, setOrderDay] = useState<DayOfWeek>("MON");
  const [inventoryDay, setInventoryDay] = useState<DayOfWeek>("SUN");
  const [savedMsg, setSavedMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // 서버 값으로 초기화 (미설정/null 필드는 기본값 유지)
  useEffect(() => {
    if (settings) {
      if (settings.openTime) setOpenTime(toHHmm(settings.openTime));
      if (settings.closeTime) setCloseTime(toHHmm(settings.closeTime));
      if (settings.orderDay) setOrderDay(settings.orderDay);
      if (settings.inventoryDay) setInventoryDay(settings.inventoryDay);
    }
  }, [settings]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSavedMsg(null);
    if (!openTime || !closeTime) {
      setErrorMsg("영업 시간을 입력해주세요.");
      return;
    }
    try {
      await updateMutation.mutateAsync({
        openTime,
        closeTime,
        orderDay,
        inventoryDay,
      });
      if (isInitial) {
        navigate("/main");
      } else {
        setSavedMsg("저장되었습니다.");
        setTimeout(() => setSavedMsg(null), 2000);
      }
    } catch (err) {
      setErrorMsg(
        err instanceof Error
          ? `저장 실패: ${err.message}`
          : "저장에 실패했습니다.",
      );
    }
  };

  const body = (
    <form onSubmit={handleSubmit} className="space-y-6">
      <Section
        title="영업 시간"
        desc="판매 반영 직후와 영업 시작 3시간 전에 모바일 앱으로 알림을 보냅니다"
      >
        <div className="grid grid-cols-2 gap-3">
          <Field label="오픈 시간">
            <input
              type="time"
              value={openTime}
              onChange={(e) => setOpenTime(e.target.value)}
              className={inputClass}
              required
            />
          </Field>
          <Field label="마감 시간">
            <input
              type="time"
              value={closeTime}
              onChange={(e) => setCloseTime(e.target.value)}
              className={inputClass}
              required
            />
          </Field>
        </div>
      </Section>

      <Section
        title="발주 요일"
        desc="다음 발주일까지 필요한 재고와 권장 구매량을 계산하는 기준입니다"
      >
        <DayPicker value={orderDay} onChange={setOrderDay} />
      </Section>

      <Section
        title="재고 실사 요일"
        desc="매장에서 재고를 확인하는 정기 일정을 기록합니다"
      >
        <DayPicker value={inventoryDay} onChange={setInventoryDay} />
      </Section>

      {errorMsg && (
        <div className="px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
          {errorMsg}
        </div>
      )}

      {savedMsg && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          className="px-4 py-3 rounded-xl bg-green-50 border border-green-200 text-sm text-green-700"
        >
          ✅ {savedMsg}
        </motion.div>
      )}

      <div className="space-y-2">
        <GradientButton
          type="submit"
          disabled={updateMutation.isPending || isLoading}
        >
          {updateMutation.isPending
            ? "저장 중..."
            : isInitial
              ? "저장 후 시작하기"
              : "저장"}
        </GradientButton>

        {isInitial && (
          <button
            type="button"
            onClick={() => navigate("/main")}
            className="w-full text-sm text-[#64748b] hover:text-[#0EA5E9] transition-colors py-2"
          >
            나중에 설정하기
          </button>
        )}
      </div>
    </form>
  );

  // 가입 직후 모드 — auth shell (사이드바 없는 풀스크린)
  if (isInitial) {
    return (
      <AppShell variant="auth">
        <div className="space-y-6">
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center lg:text-left"
          >
            <div className="w-16 h-16 mx-auto lg:mx-0 mb-4 rounded-2xl bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] flex items-center justify-center text-3xl lg:hidden">
              🏪
            </div>
            <h1 className="text-[32px] font-semibold text-[#1e293b] mb-2">
              매장 운영 설정
            </h1>
            <p className="text-[#64748b]">
              영업 시간과 발주 요일을 입력해주세요. 발주 알림과 재고 계산에
              사용됩니다.
            </p>
          </motion.div>

          {isLoading ? <Spinner /> : body}
        </div>
      </AppShell>
    );
  }

  // 일반 수정 모드 — 메인 shell
  return (
    <AppShell variant="main">
      <PageHeader
        title="매장 운영 설정"
        description="발주 알림과 재고 계산에 사용됩니다"
        backTo="/main"
      />
      {isLoading ? <Spinner /> : body}
    </AppShell>
  );
}

// ────────── sub components ──────────

const inputClass =
  "w-full px-4 py-3 rounded-xl bg-white border-2 border-[#e2e8f0] focus:border-[#0EA5E9] focus:outline-none transition-colors";

function Section({
  title,
  desc,
  children,
}: {
  title: string;
  desc?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h3 className="font-semibold text-[#1e293b] mb-1">{title}</h3>
      {desc && <p className="text-xs text-[#94a3b8] mb-3">{desc}</p>}
      {children}
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label className="block text-sm text-[#334155]">{label}</label>
      {children}
    </div>
  );
}

function DayPicker({
  value,
  onChange,
}: {
  value: DayOfWeek;
  onChange: (day: DayOfWeek) => void;
}) {
  return (
    <div className="grid grid-cols-7 gap-1.5">
      {DAY_OF_WEEK_ORDER.map((day) => {
        const isActive = value === day;
        return (
          <button
            key={day}
            type="button"
            onClick={() => onChange(day)}
            className={`py-2.5 rounded-lg text-sm font-medium transition-all ${
              isActive
                ? "bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] text-white shadow-md shadow-[#0EA5E9]/30"
                : "bg-white border-2 border-[#e2e8f0] text-[#64748b] hover:border-[#0EA5E9]"
            }`}
          >
            {DAY_OF_WEEK_LABEL[day]}
          </button>
        );
      })}
    </div>
  );
}

// ────────── helpers ──────────

/** "HH:mm:ss" 또는 "HH:mm" → "HH:mm" */
function toHHmm(t: string | null | undefined): string {
  if (!t) return "";
  return t.length >= 5 ? t.slice(0, 5) : t;
}
