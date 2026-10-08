import { useState } from "react";
import { motion } from "motion/react";
import { useNavigate } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { GradientButton } from "../components/common/GradientButton";
import { useOnboard } from "../hooks/useOnboard";
import { CATEGORY_OPTIONS } from "../types/onboard";
import type { RecipeCategory } from "../types/onboard";
import { ApiError } from "../api/client";

export default function OnboardPage() {
  const navigate = useNavigate();
  const onboardMutation = useOnboard();
  const [selected, setSelected] = useState<Set<RecipeCategory>>(new Set());
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [resultSummary, setResultSummary] = useState<{
    menus: number;
    bom: number;
    newCount: number;
  } | null>(null);

  const toggle = (value: RecipeCategory) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(value)) next.delete(value);
      else next.add(value);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    if (selected.size === 0) {
      setErrorMsg("하나 이상의 매장 카테고리를 선택해주세요.");
      return;
    }
    try {
      const result = await onboardMutation.mutateAsync({
        categories: Array.from(selected),
      });
      setResultSummary({
        menus: result.createdMenus,
        bom: result.createdBom,
        newCount: result.newIngredients.length,
      });
      // 결과를 잠시 보여준 뒤 가게 운영 설정 단계로
      setTimeout(() => navigate("/settings?initial=1"), 1800);
    } catch (err) {
      setErrorMsg(
        err instanceof ApiError && err.status === 401
          ? "로그인이 만료되었습니다. 다시 로그인해주세요."
          : err instanceof Error
            ? err.message
            : "초기 설정에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      );
    }
  };

  return (
    <AppShell variant="auth">
      <form onSubmit={handleSubmit} className="space-y-8">
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="text-center lg:text-left"
        >
          <div className="w-16 h-16 mx-auto lg:mx-0 mb-4 rounded-2xl bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] flex items-center justify-center lg:hidden">
            <svg
              className="w-8 h-8 text-white"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"
              />
            </svg>
          </div>
          <h1 className="text-[32px] font-semibold text-[#1e293b] mb-2">
            매장 카테고리 선택
          </h1>
          <p className="text-[#64748b]">
            여러 개 선택 가능합니다. 선택한 카테고리의 추천 메뉴와 재료가
            자동으로 등록됩니다.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="grid grid-cols-2 lg:grid-cols-3 gap-3"
        >
          {CATEGORY_OPTIONS.map((option) => {
            const isSelected = selected.has(option.value);
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => toggle(option.value)}
                aria-pressed={isSelected}
                className={`relative p-4 rounded-2xl border-2 text-left transition-all active:scale-[0.98] ${
                  isSelected
                    ? "border-[#0EA5E9] bg-[#F0F9FF] shadow-md shadow-[#0EA5E9]/15"
                    : "border-[#e2e8f0] bg-white hover:border-[#bae6fd]"
                }`}
              >
                <div className="text-3xl mb-2">{option.emoji}</div>
                <div
                  className={`font-semibold mb-0.5 ${
                    isSelected ? "text-[#0284c7]" : "text-[#1e293b]"
                  }`}
                >
                  {option.label}
                </div>
                <div className="text-xs text-[#64748b]">
                  {option.description}
                </div>
                {isSelected && (
                  <div className="absolute top-3 right-3 w-5 h-5 rounded-full bg-[#0EA5E9] text-white flex items-center justify-center">
                    <svg
                      className="w-3 h-3"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      strokeWidth={3}
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M5 13l4 4L19 7"
                      />
                    </svg>
                  </div>
                )}
              </button>
            );
          })}
        </motion.div>

        {errorMsg && (
          <div className="px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
            {errorMsg}
          </div>
        )}

        {resultSummary && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="px-4 py-4 rounded-xl bg-green-50 border border-green-200 text-sm text-green-800 space-y-1"
          >
            <div className="font-semibold mb-1">✅ 초기 설정 완료</div>
            <div>
              메뉴 {resultSummary.menus}개 · 재료 구성 {resultSummary.bom}개
              등록
            </div>
            {resultSummary.newCount > 0 && (
              <div className="text-xs">
                새로 추가된 재료 {resultSummary.newCount}개 — 수량은 메인 화면
                진입 후 수정하실 수 있습니다.
              </div>
            )}
            <div className="text-xs text-green-700 pt-1">
              잠시 후 메인 화면으로 이동합니다...
            </div>
          </motion.div>
        )}

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
        >
          <GradientButton
            type="submit"
            disabled={
              onboardMutation.isPending ||
              selected.size === 0 ||
              resultSummary !== null
            }
          >
            {onboardMutation.isPending
              ? "설정 중..."
              : resultSummary
                ? "이동 중..."
                : "시작하기"}
          </GradientButton>

          {!resultSummary && (
            <button
              type="button"
              onClick={() => navigate("/settings?initial=1")}
              className="w-full text-sm text-[#64748b] hover:text-[#0EA5E9] transition-colors pt-4"
            >
              건너뛰고 매장 설정으로
            </button>
          )}
        </motion.div>
      </form>
    </AppShell>
  );
}
