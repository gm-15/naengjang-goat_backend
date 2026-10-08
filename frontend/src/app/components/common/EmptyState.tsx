import type { ReactNode } from "react";

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: ReactNode;
}

export function EmptyState({ title, description, icon }: EmptyStateProps) {
  return (
    <div className="text-center py-12">
      <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-[#F0F9FF] flex items-center justify-center">
        {icon ?? (
          <svg className="w-8 h-8 text-[#0EA5E9]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        )}
      </div>
      <p className="text-[#1e293b] font-medium mb-1">{title}</p>
      {description && <p className="text-sm text-[#94a3b8]">{description}</p>}
    </div>
  );
}
