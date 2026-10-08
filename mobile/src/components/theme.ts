export const C = {
  primary: "#0EA5E9",
  primaryLight: "#38BDF8",
  primaryDark: "#0284c7",
  primarySoft: "#F0F9FF",
  primarySoft2: "#E0F2FE",
  bg: "#F5FAFF",
  card: "#FFFFFF",
  border: "#E2E8F0",
  text: "#1E293B",
  textSub: "#64748B",
  textMute: "#94A3B8",
  danger: "#EF4444",
  dangerSoft: "#FEF2F2",
  warning: "#F59E0B",
  warningSoft: "#FFFBEB",
  success: "#10B981",
  successSoft: "#ECFDF5",
} as const;

export const GRADIENT = [C.primary, C.primaryLight] as const;
export const RADIUS = { sm: 8, md: 12, lg: 16, xl: 20 } as const;
