import { motion } from "motion/react";
import { useNavigate } from "react-router";
import { AppShell } from "../components/common/AppShell";
import { PurchaseSummaryWidget } from "../components/common/PurchaseSummaryWidget";
import { authStorage } from "../lib/auth-storage";
import { queryClient } from "../lib/query-client";

export default function MainPage() {
  const navigate = useNavigate();

  const handleLogout = () => {
    if (confirm("로그아웃 하시겠습니까?")) {
      authStorage.clear();
      queryClient.clear();
      navigate("/");
    }
  };

  return (
    <AppShell variant="main">
      {/* Mobile/Tablet logout — desktop has it in sidebar */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="flex justify-end mb-4 lg:hidden"
      >
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-[#64748b] hover:text-red-500 hover:bg-red-50 transition-colors"
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
          로그아웃
        </button>
      </motion.div>

      <PurchaseSummaryWidget />

      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="text-center lg:text-left mb-10 lg:mb-12"
      >
        <div className="w-20 h-20 mx-auto lg:mx-0 mb-6 rounded-2xl bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] flex items-center justify-center lg:hidden">
          <svg
            className="w-10 h-10 text-white"
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
        <h1 className="text-[36px] lg:text-[40px] font-semibold text-[#1e293b] mb-3">
          메인 페이지
        </h1>
        <p className="text-[#64748b] lg:text-lg">원하는 서비스를 선택하세요</p>
      </motion.div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 lg:gap-6">
        <ServiceCard
          delay={0.15}
          onClick={() => navigate("/operations")}
          title="판매 마감 · 운영 리포트"
          description="POS 엑셀을 반영하고 하루 운영 결과를 확인하세요"
          icon={<span className="text-white text-2xl">✓</span>}
        />
        <ServiceCard
          delay={0.2}
          onClick={() => navigate("/lowest-price")}
          title="최저가 페이지"
          description="최저가 상품을 확인하세요"
          icon={
            <svg
              className="w-7 h-7 text-white"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          }
        />
        <ServiceCard
          delay={0.25}
          onClick={() => navigate("/inventory")}
          title="재고 관리"
          description="입고 등록과 잔여 재고 확인"
          icon={
            <svg
              className="w-7 h-7 text-white"
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
          }
        />
        <ServiceCard
          delay={0.3}
          onClick={() => navigate("/order")}
          title="발주 페이지"
          description="구매 판단 · 배송 대기 · 수령 후 입고"
          icon={
            <svg
              className="w-7 h-7 text-white"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"
              />
            </svg>
          }
        />
      </div>
    </AppShell>
  );
}

function ServiceCard({
  delay,
  onClick,
  title,
  description,
  icon,
}: {
  delay: number;
  onClick: () => void;
  title: string;
  description: string;
  icon: React.ReactNode;
}) {
  return (
    <motion.button
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
      onClick={onClick}
      className="w-full p-6 lg:p-8 rounded-2xl bg-white border-2 border-[#e2e8f0] hover:border-[#0EA5E9] hover:shadow-lg hover:shadow-[#0EA5E9]/10 active:scale-[0.98] transition-all duration-200 group text-left"
    >
      <div className="flex items-center gap-4">
        <div className="w-14 h-14 lg:w-16 lg:h-16 rounded-xl bg-gradient-to-br from-[#0EA5E9] to-[#38BDF8] flex items-center justify-center flex-shrink-0 group-hover:scale-110 transition-transform">
          {icon}
        </div>
        <div className="flex-1">
          <h2 className="text-[22px] lg:text-[24px] font-semibold text-[#1e293b] mb-1">
            {title}
          </h2>
          <p className="text-[#64748b]">{description}</p>
        </div>
        <svg
          className="w-6 h-6 text-[#94a3b8] group-hover:text-[#0EA5E9] group-hover:translate-x-1 transition-all"
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
      </div>
    </motion.button>
  );
}
