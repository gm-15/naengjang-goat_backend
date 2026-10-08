import { type ReactNode } from "react";
import { NavLink, useNavigate } from "react-router";
import { authStorage } from "../../lib/auth-storage";
import { queryClient } from "../../lib/query-client";

export type AppShellVariant = "auth" | "main";

interface AppShellProps {
  variant: AppShellVariant;
  children: ReactNode;
}

export function AppShell({ variant, children }: AppShellProps) {
  if (variant === "auth") return <AuthShell>{children}</AuthShell>;
  return <MainShell>{children}</MainShell>;
}

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen w-full bg-gradient-to-br from-[#F0F9FF] to-white lg:bg-none">
      <div className="min-h-screen w-full lg:grid lg:grid-cols-2">
        {/* Desktop hero (hidden on mobile/tablet) */}
        <div className="hidden lg:flex lg:flex-col lg:justify-between lg:p-16 xl:p-20 lg:bg-gradient-to-br lg:from-[#0EA5E9] lg:to-[#38BDF8] lg:text-white lg:min-h-screen relative overflow-hidden">
          {/* Decorative blurred circles */}
          <div className="hidden lg:block absolute -top-24 -right-24 w-96 h-96 rounded-full bg-white/10 blur-3xl pointer-events-none" />
          <div className="hidden lg:block absolute -bottom-32 -left-16 w-[28rem] h-[28rem] rounded-full bg-white/5 blur-3xl pointer-events-none" />

          <div className="flex items-center gap-3 relative">
            <div className="w-12 h-12 rounded-xl bg-white/20 backdrop-blur flex items-center justify-center">
              <svg
                className="w-7 h-7"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
                />
              </svg>
            </div>
            <span className="text-2xl font-semibold">냉장 G.O.A.T</span>
          </div>

          <div className="max-w-xl relative">
            <h2 className="text-5xl xl:text-6xl font-bold mb-6 leading-tight">
              소상공인을 위한
              <br />
              식재료 최저가·발주 관리
            </h2>
            <p className="text-lg text-white/90 leading-relaxed">
              KAMIS 공식 시세와 온라인 최저가를 한 눈에 비교하고, 재고 부족
              상품을 놓치지 마세요.
            </p>
          </div>

          <div className="text-sm text-white/70 relative">
            © 2026 냉장G.O.A.T
          </div>
        </div>

        {/* Form column */}
        <div className="flex items-center justify-center px-6 py-10 lg:px-16 lg:py-16 xl:px-24 lg:min-h-screen lg:bg-gradient-to-br lg:from-[#F0F9FF] lg:to-white">
          <div className="w-full max-w-sm lg:max-w-lg">{children}</div>
        </div>
      </div>
    </div>
  );
}

function MainShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-full bg-gradient-to-br from-[#F0F9FF] to-white">
      <div className="lg:grid lg:grid-cols-[260px_1fr] lg:min-h-screen">
        <Sidebar />
        <main className="px-0 lg:px-0">
          <div className="mx-auto w-full max-w-md md:max-w-2xl lg:max-w-5xl px-5 sm:px-6 pt-[calc(1.5rem+env(safe-area-inset-top))] pb-[calc(6rem+env(safe-area-inset-bottom))] lg:py-10">
            {children}
          </div>
        </main>
      </div>
      <MobileTabBar />
    </div>
  );
}

const NAV_ITEMS = [
  { to: "/main", label: "메인", icon: HomeIcon },
  { to: "/lowest-price", label: "최저가", icon: TagIcon },
  { to: "/inventory", label: "재고", icon: BoxIcon },
  { to: "/order", label: "발주", icon: ClipboardIcon },
  { to: "/operations", label: "마감", icon: ClipboardIcon },
  { to: "/settings", label: "설정", icon: CogIcon },
];

/** 모바일·태블릿 전용 하단 탭바 (lg 이상에서는 Sidebar 사용) */
function MobileTabBar() {
  return (
    <nav
      aria-label="주요 메뉴"
      className="lg:hidden fixed bottom-0 inset-x-0 z-40 bg-white/95 backdrop-blur border-t border-[#e2e8f0] pb-[env(safe-area-inset-bottom)]"
    >
      <ul className="mx-auto max-w-2xl grid grid-cols-6">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              className={({ isActive }) =>
                `flex flex-col items-center justify-center gap-1 h-16 text-[11px] font-medium transition-colors ${
                  isActive
                    ? "text-[#0EA5E9]"
                    : "text-[#94a3b8] active:text-[#0EA5E9]"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    className={`flex items-center justify-center w-10 h-7 rounded-full transition-colors ${
                      isActive ? "bg-[#E0F2FE]" : ""
                    }`}
                  >
                    <Icon />
                  </span>
                  {label}
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function Sidebar() {
  const navigate = useNavigate();

  const items = NAV_ITEMS.map((item) =>
    item.to === "/inventory"
      ? { ...item, label: "재고 관리" }
      : item.to === "/order"
        ? { ...item, label: "발주 관리" }
        : item,
  );

  const handleLogout = () => {
    if (confirm("로그아웃 하시겠습니까?")) {
      authStorage.clear();
      queryClient.clear();
      navigate("/");
    }
  };

  return (
    <aside className="hidden lg:flex lg:flex-col lg:bg-white lg:border-r-2 lg:border-[#e2e8f0] lg:p-6 lg:sticky lg:top-0 lg:h-screen">
      <div
        onClick={() => navigate("/main")}
        className="flex items-center gap-3 mb-10 cursor-pointer"
      >
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] flex items-center justify-center">
          <svg
            className="w-5 h-5 text-white"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
            />
          </svg>
        </div>
        <span className="font-semibold text-[#1e293b]">냉장 G.O.A.T</span>
      </div>

      <nav className="flex-1 space-y-1">
        {items.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-xl transition-all ${
                isActive
                  ? "bg-gradient-to-r from-[#0EA5E9] to-[#38BDF8] text-white shadow-md shadow-[#0EA5E9]/20"
                  : "text-[#64748b] hover:bg-[#F0F9FF] hover:text-[#0EA5E9]"
              }`
            }
          >
            <Icon />
            <span className="font-medium">{label}</span>
          </NavLink>
        ))}
      </nav>

      <button
        onClick={handleLogout}
        className="flex items-center gap-3 px-4 py-3 rounded-xl text-[#64748b] hover:bg-red-50 hover:text-red-500 transition-colors"
      >
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
            d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
          />
        </svg>
        <span className="font-medium">로그아웃</span>
      </button>
    </aside>
  );
}

function HomeIcon() {
  return (
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
        d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
      />
    </svg>
  );
}

function TagIcon() {
  return (
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
        d="M7 7h.01M7 3h5a1.99 1.99 0 011.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.99 1.99 0 013 12V7a4 4 0 014-4z"
      />
    </svg>
  );
}

function ClipboardIcon() {
  return (
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
        d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
      />
    </svg>
  );
}

function BoxIcon() {
  return (
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
        d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"
      />
    </svg>
  );
}

function CogIcon() {
  return (
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
        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
      />
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
      />
    </svg>
  );
}
