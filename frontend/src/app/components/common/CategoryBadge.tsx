interface CategoryBadgeProps {
  label: string;
  tone?: "primary" | "danger" | "warning" | "success";
}

export function CategoryBadge({ label, tone = "primary" }: CategoryBadgeProps) {
  const styles: Record<NonNullable<CategoryBadgeProps["tone"]>, string> = {
    primary: "bg-[#F0F9FF] text-[#0EA5E9]",
    danger: "bg-red-500 text-white",
    warning: "bg-orange-500 text-white",
    success: "bg-yellow-500 text-white",
  };

  return (
    <span className={`text-xs px-2 py-0.5 rounded-md ${styles[tone]}`}>
      {label}
    </span>
  );
}
