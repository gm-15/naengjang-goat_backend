import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "outline";

interface GradientButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  fullWidth?: boolean;
  children: ReactNode;
}

export function GradientButton({
  variant = "primary",
  fullWidth = true,
  className = "",
  children,
  ...rest
}: GradientButtonProps) {
  const base =
    "py-3.5 rounded-xl font-medium transition-all duration-200 active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed disabled:active:scale-100";

  const styles =
    variant === "primary"
      ? "bg-gradient-to-r from-[#0EA5E9] to-[#38BDF8] text-white shadow-lg shadow-[#0EA5E9]/25 hover:shadow-xl hover:shadow-[#0EA5E9]/30 hover:scale-[1.02]"
      : "border-2 border-[#e2e8f0] text-[#64748b] hover:bg-[#f8fafc]";

  return (
    <button
      {...rest}
      className={`${base} ${styles} ${fullWidth ? "w-full" : "px-5 whitespace-nowrap"} ${className}`}
    >
      {children}
    </button>
  );
}
