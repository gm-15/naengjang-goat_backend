import { useNavigate } from "react-router";
import { motion } from "motion/react";

interface PageHeaderProps {
  title: string;
  description?: string;
  backTo?: string;
  showBackOnDesktop?: boolean;
  right?: React.ReactNode;
}

export function PageHeader({
  title,
  description,
  backTo,
  showBackOnDesktop = false,
  right,
}: PageHeaderProps) {
  const navigate = useNavigate();

  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      className="mb-6"
    >
      {backTo && (
        <button
          onClick={() => navigate(backTo)}
          className={`flex items-center gap-2 text-[#64748b] hover:text-[#0EA5E9] transition-colors mb-4 ${
            showBackOnDesktop ? "" : "lg:hidden"
          }`}
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          뒤로 가기
        </button>
      )}

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-[28px] lg:text-[32px] font-semibold text-[#1e293b] mb-1">
            {title}
          </h1>
          {description && (
            <p className="text-[#64748b]">{description}</p>
          )}
        </div>
        {right && <div className="flex-shrink-0">{right}</div>}
      </div>
    </motion.div>
  );
}
