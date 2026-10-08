import { useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { useNavigate } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { GradientButton } from "../components/common/GradientButton";
import { useLogin, useSignup } from "../hooks/useAuth";
import type { SignupPayload } from "../types/user";
import { ApiError } from "../api/client";

export default function LoginPage({
  initialMode = "login",
}: {
  initialMode?: "login" | "signup";
}) {
  const [isLogin, setIsLogin] = useState(initialMode === "login");

  return (
    <AppShell variant="auth">
      <AnimatePresence mode="wait">
        {isLogin ? (
          <motion.div
            key="login"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
          >
            <LoginScreen onSwitchToSignup={() => setIsLogin(false)} />
          </motion.div>
        ) : (
          <motion.div
            key="signup"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
          >
            <SignupScreen onSwitchToLogin={() => setIsLogin(true)} />
          </motion.div>
        )}
      </AnimatePresence>
    </AppShell>
  );
}

const inputClass =
  "w-full px-4 py-3.5 rounded-xl bg-white border-2 border-[#e2e8f0] focus:border-[#0EA5E9] focus:outline-none transition-colors placeholder:text-[#94a3b8]";

function LoginScreen({ onSwitchToSignup }: { onSwitchToSignup: () => void }) {
  const navigate = useNavigate();
  const loginMutation = useLogin();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    try {
      await loginMutation.mutateAsync({ username, password });
      navigate("/main");
    } catch (err) {
      setErrorMsg(
        err instanceof ApiError && err.status === 401
          ? "아이디 또는 비밀번호가 올바르지 않습니다."
          : "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      );
    }
  };

  return (
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
              d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
            />
          </svg>
        </div>
        <h1 className="text-[32px] font-semibold text-[#1e293b] mb-2">
          로그인
        </h1>
        <p className="text-[#64748b]">계정에 로그인하세요</p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="space-y-5"
      >
        <div className="space-y-2">
          <label className="block text-sm text-[#334155]">아이디</label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="아이디를 입력하세요"
            className={inputClass}
            autoComplete="username"
            required
          />
        </div>

        <div className="space-y-2">
          <label className="block text-sm text-[#334155]">비밀번호</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            className={inputClass}
            autoComplete="current-password"
            required
          />
        </div>

        {errorMsg && (
          <div className="px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
            {errorMsg}
          </div>
        )}

        <GradientButton type="submit" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? "로그인 중..." : "로그인"}
        </GradientButton>

        <button
          type="button"
          className="w-full text-sm text-[#64748b] hover:text-[#0EA5E9] transition-colors"
        >
          비밀번호를 잊으셨나요?
        </button>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
        className="text-center pt-4 border-t border-[#e2e8f0]"
      >
        <span className="text-[#64748b]">계정이 없으신가요? </span>
        <button
          type="button"
          onClick={onSwitchToSignup}
          className="text-[#0EA5E9] font-medium hover:underline"
        >
          회원가입
        </button>
      </motion.div>
    </form>
  );
}

function SignupScreen({ onSwitchToLogin }: { onSwitchToLogin: () => void }) {
  const navigate = useNavigate();
  const signupMutation = useSignup();
  const [form, setForm] = useState<SignupPayload>({
    username: "",
    password: "",
    ownerName: "",
  });
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const setField = <K extends keyof SignupPayload>(
    key: K,
    value: SignupPayload[K],
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    try {
      await signupMutation.mutateAsync(form);
      // 회원가입 직후 → 온보딩 (Phase C에서 OnboardPage 추가 예정)
      navigate("/onboard");
    } catch (err) {
      setErrorMsg(
        err instanceof ApiError && err.status === 409
          ? "이미 사용 중인 아이디입니다."
          : err instanceof Error
            ? err.message
            : "회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      );
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6 pb-8">
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
              d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"
            />
          </svg>
        </div>
        <h1 className="text-[32px] font-semibold text-[#1e293b] mb-2">
          회원가입
        </h1>
        <p className="text-[#64748b]">간단한 정보로 시작하세요</p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="space-y-4"
      >
        <Field label="아이디">
          <input
            type="text"
            value={form.username}
            onChange={(e) => setField("username", e.target.value)}
            placeholder="영문 / 숫자 4자 이상"
            className={inputClass}
            minLength={4}
            autoComplete="username"
            required
          />
        </Field>

        <Field label="비밀번호">
          <input
            type="password"
            value={form.password}
            onChange={(e) => setField("password", e.target.value)}
            placeholder="8자 이상"
            className={inputClass}
            minLength={8}
            autoComplete="new-password"
            required
          />
        </Field>

        <Field label="대표자명">
          <input
            type="text"
            value={form.ownerName}
            onChange={(e) => setField("ownerName", e.target.value)}
            placeholder="홍길동"
            className={inputClass}
            required
          />
        </Field>

        <p className="text-xs text-[#94a3b8] pt-1">
          가게 정보(매장명, 운영시간, 업종 등)는 가입 후 매장 카테고리 선택
          단계에서 입력합니다.
        </p>

        {errorMsg && (
          <div className="px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
            {errorMsg}
          </div>
        )}

        <GradientButton type="submit" disabled={signupMutation.isPending}>
          {signupMutation.isPending ? "가입 중..." : "가입하기"}
        </GradientButton>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
        className="text-center pt-4 border-t border-[#e2e8f0]"
      >
        <span className="text-[#64748b]">이미 계정이 있으신가요? </span>
        <button
          type="button"
          onClick={onSwitchToLogin}
          className="text-[#0EA5E9] font-medium hover:underline"
        >
          로그인
        </button>
      </motion.div>
    </form>
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
    <div className="space-y-2">
      <label className="block text-sm text-[#334155]">{label}</label>
      {children}
    </div>
  );
}
